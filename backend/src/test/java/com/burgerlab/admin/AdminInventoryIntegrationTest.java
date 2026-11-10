package com.burgerlab.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.burgerlab.admin.dto.AdminStockAdjustmentRequest;
import com.burgerlab.admin.dto.AdminStockBatchLineRequest;
import com.burgerlab.admin.dto.AdminStockBatchRequest;
import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.auth.UserRole;
import com.burgerlab.common.error.BusinessConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.order.timeout-scan.enabled=false", "app.cache.menu.enabled=false"}
)
@Testcontainers
class AdminInventoryIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.13")
            .withDatabaseName("burger_order")
            .withUsername("burger_test")
            .withPassword("burger_test_password");

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminInventoryService adminInventoryService;

    @Autowired
    private AdminStockBatchService adminStockBatchService;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM admin_stock_batch_lines");
        jdbcTemplate.update("DELETE FROM admin_stock_batches");
        jdbcTemplate.update("DELETE FROM stock WHERE product_id IN (SELECT id FROM products WHERE product_code LIKE 'PAGE-%')");
        jdbcTemplate.update("DELETE FROM products WHERE product_code LIKE 'PAGE-%'");
        jdbcTemplate.update("DELETE FROM admin_audit_logs");
        jdbcTemplate.update("DELETE FROM stock_logs");
        jdbcTemplate.update("""
                UPDATE products SET price = 32.00, on_sale = 1, version = 0 WHERE id = 1001
                """);
        jdbcTemplate.update("""
                UPDATE stock SET available = 50, reserved = 0, version = 0
                 WHERE store_id = 1 AND product_id = 1001
                """);
        jdbcTemplate.update("""
                UPDATE stock SET available = 100, reserved = 0, version = 0
                 WHERE store_id = 1 AND product_id = 1006
                """);
    }

    @Test
    void adminUpdatesPriceAndOnSaleWithOptimisticLock() throws Exception {
        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");

        JsonNode product = adminProduct(admin, 1, 1001);
        assertThat(product.path("price").asText()).isEqualTo("32.00");
        assertThat(product.path("onSale").asBoolean()).isTrue();
        assertThat(product.path("version").asInt()).isZero();

        HttpResponse<String> hidden = patch(admin, "/api/admin/products/1001",
                Map.of("price", "33.50", "onSale", false, "version", 0));
        assertThat(hidden.statusCode()).isEqualTo(200);
        assertThat(json(hidden).path("price").asText()).isEqualTo("33.50");
        assertThat(json(hidden).path("onSale").asBoolean()).isFalse();
        assertThat(json(hidden).path("version").asInt()).isEqualTo(1);

        HttpResponse<String> menu = get(admin, "/api/stores/1/menu");
        assertThat(menu.statusCode()).isEqualTo(200);
        assertThat(menu.body()).doesNotContain("S1-CLASSIC");

        HttpResponse<String> restored = patch(admin, "/api/admin/products/1001",
                Map.of("price", "32.00", "onSale", true, "version", 1));
        assertThat(restored.statusCode()).isEqualTo(200);
        assertThat(json(restored).path("version").asInt()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs", Integer.class)).isEqualTo(2);
    }

    @Test
    void adminAdjustsStockAndStockLogIsAuditable() throws Exception {
        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");

        HttpResponse<String> adjusted = post(admin,
                "/api/admin/products/1001/stock-adjustments",
                Map.of("delta", 5, "version", 0, "reason", "到货补货"));
        assertThat(adjusted.statusCode()).isEqualTo(200);
        assertThat(json(adjusted).path("available").asInt()).isEqualTo(55);
        assertThat(json(adjusted).path("reserved").asInt()).isZero();
        assertThat(json(adjusted).path("version").asInt()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_logs
                 WHERE change_type = 'ADJUST' AND order_id IS NULL
                   AND available_delta = 5 AND operator_user_id IS NOT NULL
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs", Integer.class)).isEqualTo(1);
    }

    @Test
    void permissionsAndStaleVersionsAreEnforced() throws Exception {
        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");
        assertThat(post(admin, "/api/admin/products/1001/stock-adjustments",
                Map.of("delta", 5, "version", 0, "reason", "到货")).statusCode())
                .isEqualTo(200);

        HttpResponse<String> stale = post(admin,
                "/api/admin/products/1001/stock-adjustments",
                Map.of("delta", 1, "version", 0, "reason", "重复提交"));
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(json(stale).path("code").asText()).isEqualTo("VERSION_CONFLICT");

        HttpResponse<String> negative = post(admin,
                "/api/admin/products/1001/stock-adjustments",
                Map.of("delta", -100, "version", 1, "reason", "盘点"));
        assertThat(negative.statusCode()).isEqualTo(409);
        assertThat(json(negative).path("code").asText()).isEqualTo("STOCK_INSUFFICIENT");

        BrowserClient staff = newClient();
        login(staff, "demo_staff", "Burger@123");
        assertThat(get(staff, "/api/admin/stores/1/products").statusCode()).isEqualTo(403);
        assertThat(post(staff, "/api/admin/products/1001/stock-adjustments",
                Map.of("delta", 1, "version", 1, "reason", "越权")).statusCode())
                .isEqualTo(403);

        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        assertThat(patch(customer, "/api/admin/products/1001",
                Map.of("price", "1.00", "onSale", true, "version", 2)).statusCode())
                .isEqualTo(403);
    }

    @Test
    void concurrentBatchWithSameKeyAppliesStockOnce() throws Exception {
        AppUserPrincipal admin = new AppUserPrincipal(
                3L, "demo_admin", "unused", "演示管理员", UserRole.ADMIN, true);
        AdminStockBatchRequest request = new AdminStockBatchRequest(List.of(
                new AdminStockBatchLineRequest(1001L, 1, "并发批次")));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AdminStockBatchResult> first = executor.submit(
                    () -> createBatchAfterBarrier(admin, request, ready, start));
            Future<AdminStockBatchResult> second = executor.submit(
                    () -> createBatchAfterBarrier(admin, request, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            AdminStockBatchResult one = first.get(15, TimeUnit.SECONDS);
            AdminStockBatchResult two = second.get(15, TimeUnit.SECONDS);
            assertThat(List.of(one.replayed(), two.replayed()))
                    .containsExactlyInAnyOrder(false, true);
            assertThat(one.response().batchId()).isEqualTo(two.response().batchId());
            assertThat(stock("1001")).containsExactly(51, 0);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_stock_batches", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_stock_batch_lines", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM stock_logs WHERE change_type='ADJUST'
                    """, Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_audit_logs", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private AdminStockBatchResult createBatchAfterBarrier(
            AppUserPrincipal admin,
            AdminStockBatchRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return adminStockBatchService.create(
                1L, request, "batch-concurrent-key-1", admin);
    }

    @Test
    void previewShowsPerLineResultsWithoutWriting() throws Exception {
        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");
        Object body = Map.of("lines", List.of(
                Map.of("productId", 1001, "delta", 2, "reason", "预览补货"),
                Map.of("productId", 1006, "delta", -1, "reason", "预览盘点")));

        HttpResponse<String> preview = postPreview(admin, body);

        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(json(preview).path("valid").asBoolean()).isTrue();
        assertThat(json(preview).path("errorCount").asInt()).isZero();
        assertThat(json(preview).path("lines").get(0).path("availableBefore").asInt())
                .isEqualTo(50);
        assertThat(json(preview).path("lines").get(0).path("availableAfter").asInt())
                .isEqualTo(52);
        assertThat(stock("1001")).containsExactly(50, 0);
        assertThat(stock("1006")).containsExactly(100, 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_stock_batches", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_logs", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs", Integer.class)).isZero();
    }

    @Test
    void previewReportsEveryInvalidLineAndDoesNotPartiallyApply() throws Exception {
        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");
        Object body = Map.of("lines", List.of(
                Map.of("productId", 1001, "delta", 1, "reason", "合法行"),
                Map.of("productId", 1002, "delta", 0, "reason", "零调整"),
                Map.of("productId", 1002, "delta", 1, "reason", "重复商品"),
                Map.of("productId", 1006, "delta", -1000, "reason", "库存不足")));

        HttpResponse<String> preview = postPreview(admin, body);

        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(json(preview).path("valid").asBoolean()).isFalse();
        assertThat(json(preview).path("errorCount").asInt()).isEqualTo(3);
        assertThat(json(preview).path("lines").get(1).path("code").asText())
                .isEqualTo("INVALID_STOCK_DELTA");
        assertThat(json(preview).path("lines").get(2).path("code").asText())
                .isEqualTo("DUPLICATE_BATCH_PRODUCT");
        assertThat(json(preview).path("lines").get(3).path("code").asText())
                .isEqualTo("STOCK_INSUFFICIENT");
        assertThat(stock("1001")).containsExactly(50, 0);
        assertThat(stock("1006")).containsExactly(100, 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_stock_batches", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_logs", Integer.class)).isZero();
    }

    @Test
    void previewRequiresAdmin() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        HttpResponse<String> response = postPreview(customer, Map.of("lines", List.of(
                Map.of("productId", 1001, "delta", 1, "reason", "越权预览"))));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void batchAdjustmentsApplyAllAndReplayWithSameKey() throws Exception {
        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");
        Object body = Map.of("lines", List.of(
                Map.of("productId", 1001, "delta", 2, "reason", "批量补货"),
                Map.of("productId", 1006, "delta", -1, "reason", "批量盘点")));

        HttpResponse<String> first = postBatch(admin, body, "batch-key-0001");
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(json(first).path("appliedCount").asInt()).isEqualTo(2);
        assertThat(stock("1001")).containsExactly(52, 0);
        assertThat(stock("1006")).containsExactly(99, 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_stock_batches", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_stock_batch_lines", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE action='STOCK_BATCH'",
                Integer.class)).isEqualTo(1);

        HttpResponse<String> replay = postBatch(admin, body, "batch-key-0001");
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(replay.headers().firstValue("Idempotent-Replayed").orElse(""))
                .isEqualTo("true");
        assertThat(json(replay).path("batchId").asLong())
                .isEqualTo(json(first).path("batchId").asLong());
        assertThat(stock("1001")).containsExactly(52, 0);
        assertThat(stock("1006")).containsExactly(99, 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_stock_batches", Integer.class)).isEqualTo(1);

        HttpResponse<String> changed = postBatch(admin, Map.of("lines", List.of(
                Map.of("productId", 1001, "delta", 1, "reason", "不同请求"))),
                "batch-key-0001");
        assertThat(changed.statusCode()).isEqualTo(409);
        assertThat(json(changed).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void invalidBatchLineRollsBackEveryRow() throws Exception {
        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");
        Object body = Map.of("lines", List.of(
                Map.of("productId", 1001, "delta", 1, "reason", "先处理"),
                Map.of("productId", 1006, "delta", -1000, "reason", "非法扣减")));

        HttpResponse<String> response = postBatch(admin, body, "batch-fail-key-01");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).path("code").asText()).isEqualTo("STOCK_INSUFFICIENT");
        assertThat(stock("1001")).containsExactly(50, 0);
        assertThat(stock("1006")).containsExactly(100, 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_stock_batches", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_stock_batch_lines", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_logs", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs", Integer.class)).isZero();
    }

    @Test
    void batchRequiresAdminAndIdempotencyKey() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        assertThat(postBatch(customer, Map.of("lines", List.of(
                Map.of("productId", 1001, "delta", 1, "reason", "越权"))),
                "batch-forbidden-key").statusCode()).isEqualTo(403);

        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");
        HttpResponse<String> missing = postBatch(admin, Map.of("lines", List.of(
                Map.of("productId", 1001, "delta", 1, "reason", "缺键"))), null);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(json(missing).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void adminProductPagesAreStableAndFilterable() throws Exception {
        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");

        HttpResponse<String> first = get(admin,
                "/api/admin/stores/1/products/page?limit=3");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(json(first).path("items").size()).isEqualTo(3);
        assertThat(json(first).path("hasMore").asBoolean()).isTrue();
        long cursor = json(first).path("nextCursor").asLong();

        HttpResponse<String> second = get(admin,
                "/api/admin/stores/1/products/page?limit=3&afterId=" + cursor);
        assertThat(second.statusCode()).isEqualTo(200);
        long firstPageLastId = json(first).path("items").get(2).path("id").asLong();
        assertThat(json(second).path("items").get(0).path("id").asLong())
                .isGreaterThan(firstPageLastId);

        HttpResponse<String> offSale = get(admin,
                "/api/admin/stores/1/products/page?limit=10&onSale=false");
        assertThat(offSale.statusCode()).isEqualTo(200);
        assertThat(json(offSale).path("items").size()).isEqualTo(1);
        assertThat(json(offSale).path("items").get(0).path("code").asText())
                .isEqualTo("S1-SHAKE");
    }

    @Test
    void deepCursorPageDoesNotDependOnOffset() throws Exception {
        for (int i = 1; i <= 60; i++) {
            String code = String.format("PAGE-%03d", i);
            jdbcTemplate.update("""
                    INSERT INTO products
                        (store_id, product_code, name, description, category_code,
                         category_name, category_sort, price, on_sale, version, sort_order)
                    VALUES (1, ?, ?, '', 'PAGE', '分页测试', 9, 10.00, 1, 0, ?)
                    """, code, "分页商品" + i, i);
            Long productId = jdbcTemplate.queryForObject(
                    "SELECT id FROM products WHERE product_code = ?", Long.class, code);
            jdbcTemplate.update("""
                    INSERT INTO stock (store_id, product_id, available, reserved, version)
                    VALUES (1, ?, 10, 0, 0)
                    """, productId);
        }
        long afterId = jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE product_code = 'PAGE-050'", Long.class);

        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");
        HttpResponse<String> page = get(admin,
                "/api/admin/stores/1/products/page?limit=5&afterId=" + afterId);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(json(page).path("items").size()).isEqualTo(5);
        for (JsonNode item : json(page).path("items")) {
            assertThat(item.path("id").asLong()).isGreaterThan(afterId);
        }
        assertThat(json(page).path("hasMore").asBoolean()).isTrue();
    }

    @Test
    void concurrentStockAdjustmentsWithSameVersionHaveOneWinner() throws Exception {
        AppUserPrincipal admin = new AppUserPrincipal(
                3L, "demo_admin", "unused", "演示管理员", UserRole.ADMIN, true);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> adjustAfterBarrier(admin, ready, start));
            Future<?> second = executor.submit(() -> adjustAfterBarrier(admin, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<?> future : List.of(first, second)) {
                try {
                    future.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(BusinessConflictException.class);
                    assertThat(((BusinessConflictException) exception.getCause()).code())
                            .isEqualTo("VERSION_CONFLICT");
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT available FROM stock WHERE store_id=1 AND product_id=1001
                    """, Integer.class)).isEqualTo(51);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM stock_logs WHERE change_type='ADJUST'
                    """, Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_audit_logs", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Object adjustAfterBarrier(
            AppUserPrincipal admin,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return adminInventoryService.adjustStock(
                1001L,
                new AdminStockAdjustmentRequest(1, 0, "并发补货"),
                admin);
    }

    private HttpResponse<String> postPreview(
            BrowserClient client, Object body
    ) throws Exception {
        String csrf = fetchCsrf(client);
        return client.http().send(HttpRequest.newBuilder(
                        uri("/api/admin/stores/1/stock-adjustments/batch/preview"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postBatch(
            BrowserClient client, Object body, String idempotencyKey
    ) throws Exception {
        String csrf = fetchCsrf(client);
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        uri("/api/admin/stores/1/stock-adjustments/batch"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return client.http().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<Integer> stock(String productId) throws Exception {
        return jdbcTemplate.queryForObject("""
                SELECT available, reserved FROM stock WHERE product_id = ?
                """, (rs, rowNum) -> List.of(rs.getInt("available"), rs.getInt("reserved")),
                Long.parseLong(productId));
    }

    private JsonNode adminProduct(BrowserClient client, long storeId, long productId)
            throws Exception {
        HttpResponse<String> response = get(
                client, "/api/admin/stores/" + storeId + "/products");
        assertThat(response.statusCode()).isEqualTo(200);
        for (JsonNode item : json(response)) {
            if (item.path("id").asLong() == productId) {
                return item;
            }
        }
        throw new AssertionError("product not found: " + productId);
    }

    private HttpResponse<String> post(
            BrowserClient client, String path, Object body
    ) throws Exception {
        return send(client, path, "POST", body);
    }

    private HttpResponse<String> patch(
            BrowserClient client, String path, Object body
    ) throws Exception {
        return send(client, path, "PATCH", body);
    }

    private HttpResponse<String> send(
            BrowserClient client, String path, String method, Object body
    ) throws Exception {
        String csrf = fetchCsrf(client);
        return client.http().send(HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .method(method, HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(BrowserClient client, String path) throws Exception {
        return client.http().send(HttpRequest.newBuilder(uri(path))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private void login(BrowserClient client, String username, String password) throws Exception {
        String csrf = fetchCsrf(client);
        HttpResponse<String> response = client.http().send(HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        Map.of("username", username, "password", password))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private String fetchCsrf(BrowserClient client) throws Exception {
        HttpResponse<String> response = client.http().send(HttpRequest.newBuilder(uri("/api/auth/csrf"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response).path("token").asText();
    }

    private JsonNode json(HttpResponse<String> response) throws IOException {
        return objectMapper.readTree(response.body());
    }

    private BrowserClient newClient() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new BrowserClient(http);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private record BrowserClient(HttpClient http) {
    }
}
