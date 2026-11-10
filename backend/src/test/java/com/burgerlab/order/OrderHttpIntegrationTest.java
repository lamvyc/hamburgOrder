package com.burgerlab.order;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Order HTTP contract against real Tomcat + MySQL. Concurrency lives in the
 * service-level integration test so both HTTP and database guarantees are
 * exercised.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"app.order.timeout-scan.enabled=false", "app.cache.menu.enabled=false"})
@Testcontainers
class OrderHttpIntegrationTest {

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

    @BeforeEach
    void resetOrderState() {
        jdbcTemplate.update("DELETE FROM stock_logs");
        jdbcTemplate.update("DELETE FROM user_coupons");
        jdbcTemplate.update("DELETE FROM order_events");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM idempotency_records");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("""
                UPDATE stock
                   SET available = 50, reserved = 0, version = 0
                 WHERE product_id = 1001
                """);
        jdbcTemplate.update("""
                UPDATE stock
                   SET available = 100, reserved = 0, version = 0
                 WHERE product_id = 1006
                """);
        jdbcTemplate.update("""
                UPDATE coupon_templates
                   SET claimed_quantity = 0, status = 'ACTIVE', version = 0,
                       total_quantity = CASE code WHEN 'WELCOME5' THEN 100 ELSE 50 END
                """);
    }

    @Test
    void customerCreatesOrderReservesStockAndReadsOwnDetail() throws Exception {
        BrowserClient client = newClient();
        login(client, "demo_customer", "Burger@123");

        HttpResponse<String> created = postOrder(client, orderBody(1, 1001, 2, 1006, 1),
                "order-key-test-0001");

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Idempotent-Replayed")).hasValue("false");
        assertThat(created.headers().firstValue(HttpHeaders.LOCATION))
                .hasValueSatisfying(value -> assertThat(value).startsWith("/api/orders/"));

        JsonNode order = json(created);
        long orderId = order.path("id").asLong();
        assertThat(order.path("status").asText()).isEqualTo("WAIT_PAY");
        assertThat(order.path("storeName").asText()).isEqualTo("中央车站店");
        assertThat(order.path("totalAmount").asText()).isEqualTo("74.00");
        assertThat(order.path("items").size()).isEqualTo(2);
        assertThat(order.path("items").get(0).path("unitPrice").asText()).isEqualTo("32.00");
        assertThat(order.path("expiresAt").asText()).isNotBlank();

        assertThat(stock("1001")).containsExactly(48, 2);
        assertThat(stock("1006")).containsExactly(99, 1);
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("order_items")).isEqualTo(2);
        assertThat(count("order_events")).isEqualTo(1);
        assertThat(count("stock_logs")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM idempotency_records WHERE idempotency_key = ?",
                String.class, "order-key-test-0001")).isEqualTo("COMPLETED");

        HttpResponse<String> detail = get(client, "/api/orders/" + orderId);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json(detail).path("orderNo").asText())
                .isEqualTo(order.path("orderNo").asText());
    }

    @Test
    void sameKeyReplaysOriginalAndDifferentBodyConflicts() throws Exception {
        BrowserClient client = newClient();
        login(client, "demo_customer", "Burger@123");
        Map<String, Object> body = orderBody(1, 1001, 1);

        HttpResponse<String> first = postOrder(client, body, "order-key-replay-0001");
        assertThat(first.statusCode()).isEqualTo(201);
        long firstId = json(first).path("id").asLong();

        HttpResponse<String> replay = postOrder(client, body, "order-key-replay-0001");
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(json(replay).path("id").asLong()).isEqualTo(firstId);
        assertThat(count("orders")).isEqualTo(1);
        assertThat(stock("1001")).containsExactly(49, 1);

        HttpResponse<String> conflict = postOrder(client, orderBody(1, 1001, 2),
                "order-key-replay-0001");
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json(conflict).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(count("orders")).isEqualTo(1);
        assertThat(stock("1001")).containsExactly(49, 1);
    }

    @Test
    void insufficientSecondItemRollsBackEverything() throws Exception {
        jdbcTemplate.update("UPDATE stock SET available = 0 WHERE product_id = 1006");
        BrowserClient client = newClient();
        login(client, "demo_customer", "Burger@123");

        HttpResponse<String> response = postOrder(
                client, orderBody(1, 1001, 1, 1006, 1), "order-key-rollback-0001");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).path("code").asText()).isEqualTo("STOCK_INSUFFICIENT");
        assertThat(count("orders")).isZero();
        assertThat(count("order_items")).isZero();
        assertThat(count("stock_logs")).isZero();
        assertThat(count("idempotency_records")).isZero();
        assertThat(stock("1001")).containsExactly(50, 0);
    }

    @Test
    void anotherCustomerCannotReadTheOrder() throws Exception {
        BrowserClient owner = newClient();
        login(owner, "demo_customer", "Burger@123");
        long orderId = json(postOrder(owner, orderBody(1, 1001, 1), "order-key-owner-0001"))
                .path("id").asLong();

        BrowserClient other = newClient();
        login(other, "demo_customer_two", "Burger@123");
        HttpResponse<String> response = get(other, "/api/orders/" + orderId);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json(response).path("code").asText()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void staffCannotCreateOrReadCustomerOrders() throws Exception {
        BrowserClient staff = newClient();
        login(staff, "demo_staff", "Burger@123");

        assertThat(postOrder(staff, orderBody(1, 1001, 1), "order-key-staff-0001").statusCode())
                .isEqualTo(403);
        assertThat(get(staff, "/api/orders/1").statusCode()).isEqualTo(403);
    }

    @Test
    void missingIdempotencyKeyAndDuplicateItemsAreBadRequests() throws Exception {
        BrowserClient client = newClient();
        login(client, "demo_customer", "Burger@123");

        HttpResponse<String> noKey = postOrder(client, orderBody(1, 1001, 1), null);
        assertThat(noKey.statusCode()).isEqualTo(400);
        assertThat(json(noKey).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        Map<String, Object> duplicateItems = Map.of(
                "storeId", 1,
                "items", List.of(
                        Map.of("productId", 1001, "quantity", 1),
                        Map.of("productId", 1001, "quantity", 2)));
        HttpResponse<String> duplicate = postOrder(client, duplicateItems, "order-key-dup-0001");
        assertThat(duplicate.statusCode()).isEqualTo(400);
        assertThat(json(duplicate).path("code").asText()).isEqualTo("DUPLICATE_ORDER_ITEM");
    }

    @Test
    void customerClaimsCouponAndCannotClaimItTwice() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        long templateId = couponTemplateId("WELCOME5");

        HttpResponse<String> available = get(customer, "/api/coupons/available");
        assertThat(available.statusCode()).isEqualTo(200);
        assertThat(json(available).get(0).path("code").asText()).isEqualTo("WELCOME5");
        assertThat(json(available).get(0).path("claimed").asBoolean()).isFalse();

        HttpResponse<String> claimed = postNoBody(
                customer, "/api/coupons/" + templateId + "/claim");
        assertThat(claimed.statusCode()).isEqualTo(201);
        assertThat(json(claimed).path("status").asText()).isEqualTo("UNUSED");
        assertThat(json(claimed).path("discountAmount").asText()).isEqualTo("5.00");

        HttpResponse<String> duplicate = postNoBody(
                customer, "/api/coupons/" + templateId + "/claim");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json(duplicate).path("code").asText()).isEqualTo("COUPON_ALREADY_CLAIMED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT claimed_quantity FROM coupon_templates WHERE id = ?
                """, Integer.class, templateId)).isEqualTo(1);
    }

    @Test
    void couponDiscountIsAppliedAndCancelReleasesTheCoupon() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        long templateId = couponTemplateId("WELCOME5");
        long couponId = json(postNoBody(customer,
                "/api/coupons/" + templateId + "/claim")).path("id").asLong();

        HttpResponse<String> created = postOrder(
                customer,
                orderBodyWithCoupon(1, couponId, 1001, 2),
                "coupon-order-cancel-key-1");
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(json(created).path("totalAmount").asText()).isEqualTo("59.00");
        assertThat(json(created).path("discountAmount").asText()).isEqualTo("5.00");
        long orderId = json(created).path("id").asLong();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM user_coupons WHERE id = ?
                """, String.class, couponId)).isEqualTo("LOCKED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT order_id FROM user_coupons WHERE id = ?
                """, Long.class, couponId)).isEqualTo(orderId);

        assertThat(postNoBody(customer, "/api/orders/" + orderId + "/cancel").statusCode())
                .isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM user_coupons WHERE id = ?
                """, String.class, couponId)).isEqualTo("UNUSED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT order_id FROM user_coupons WHERE id = ?
                """, Long.class, couponId)).isNull();
    }

    @Test
    void paymentMarksCouponUsedAndItCannotBeReused() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        long templateId = couponTemplateId("WELCOME5");
        long couponId = json(postNoBody(customer,
                "/api/coupons/" + templateId + "/claim")).path("id").asLong();

        long orderId = json(postOrder(customer,
                orderBodyWithCoupon(1, couponId, 1001, 1),
                "coupon-order-pay-key-01")).path("id").asLong();
        assertThat(postNoBody(customer, "/api/orders/" + orderId + "/pay").statusCode())
                .isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM user_coupons WHERE id = ?
                """, String.class, couponId)).isEqualTo("USED");

        HttpResponse<String> reuse = postOrder(customer,
                orderBodyWithCoupon(1, couponId, 1001, 1),
                "coupon-order-reuse-key-1");
        assertThat(reuse.statusCode()).isEqualTo(409);
        assertThat(json(reuse).path("code").asText()).isEqualTo("COUPON_NOT_USABLE");
        assertThat(count("orders")).isEqualTo(1);
    }

    @Test
    void couponOwnershipAndMinimumSpendAreEnforced() throws Exception {
        BrowserClient owner = newClient();
        login(owner, "demo_customer", "Burger@123");
        long welcomeId = couponTemplateId("WELCOME5");
        long ownerCoupon = json(postNoBody(owner,
                "/api/coupons/" + welcomeId + "/claim")).path("id").asLong();

        BrowserClient other = newClient();
        login(other, "demo_customer_two", "Burger@123");
        HttpResponse<String> foreign = postOrder(other,
                orderBodyWithCoupon(1, ownerCoupon, 1001, 2),
                "coupon-foreign-key-0001");
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(json(foreign).path("code").asText()).isEqualTo("COUPON_NOT_FOUND");

        long lateId = couponTemplateId("LATE10");
        long lateCoupon = json(postNoBody(other,
                "/api/coupons/" + lateId + "/claim")).path("id").asLong();
        HttpResponse<String> tooSmall = postOrder(other,
                orderBodyWithCoupon(1, lateCoupon, 1001, 1),
                "coupon-min-spend-key-1");
        assertThat(tooSmall.statusCode()).isEqualTo(409);
        assertThat(json(tooSmall).path("code").asText()).isEqualTo("COUPON_MIN_ORDER_NOT_MET");
        assertThat(count("orders")).isZero();
    }

    @Test
    void paymentCommitsReservedStockAndWritesEvents() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        long orderId = json(postOrder(customer, orderBody(1, 1001, 2),
                "payment-success-key-01")).path("id").asLong();

        HttpResponse<String> paid = postNoBody(customer, "/api/orders/" + orderId + "/pay");
        assertThat(paid.statusCode()).isEqualTo(200);
        assertThat(json(paid).path("status").asText()).isEqualTo("WAIT_MAKE");
        assertThat(stock("1001")).containsExactly(48, 0);
        assertThat(count("stock_logs")).isEqualTo(2);
        assertThat(count("order_events")).isEqualTo(2);

        HttpResponse<String> events = get(customer, "/api/orders/" + orderId + "/events");
        assertThat(events.statusCode()).isEqualTo(200);
        assertThat(json(events).size()).isEqualTo(2);
        assertThat(json(events).get(0).path("eventType").asText()).isEqualTo("CREATED");
        assertThat(json(events).get(1).path("eventType").asText()).isEqualTo("PAYMENT_SUCCEEDED");
    }

    @Test
    void expiredAndDuplicatePaymentAreRejectedWithoutDoubleCommit() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        long expiredOrder = json(postOrder(customer, orderBody(1, 1001, 1),
                "payment-expired-key-1")).path("id").asLong();
        jdbcTemplate.update("UPDATE orders SET expires_at = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE) WHERE id = ?", expiredOrder);

        HttpResponse<String> expired = postNoBody(customer, "/api/orders/" + expiredOrder + "/pay");
        assertThat(expired.statusCode()).isEqualTo(409);
        assertThat(json(expired).path("code").asText()).isEqualTo("ORDER_EXPIRED");
        assertThat(stock("1001")).containsExactly(49, 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, expiredOrder))
                .isEqualTo("WAIT_PAY");

        long paidOrder = json(postOrder(customer, orderBody(1, 1001, 1),
                "payment-duplicate-key-1")).path("id").asLong();
        assertThat(postNoBody(customer, "/api/orders/" + paidOrder + "/pay").statusCode())
                .isEqualTo(200);
        HttpResponse<String> duplicate = postNoBody(
                customer, "/api/orders/" + paidOrder + "/pay");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json(duplicate).path("code").asText()).isEqualTo("ORDER_STATUS_CONFLICT");
        // The expired order still holds one reserved unit until A5 releases it;
        // the paid order committed its own reservation.
        assertThat(stock("1001")).containsExactly(48, 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_logs WHERE order_id = ? AND change_type = 'COMMIT'",
                Integer.class, paidOrder)).isEqualTo(1);
    }

    @Test
    void staffSeesOnlyAssignedStoreAndCanAdvanceOrderStatus() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        long orderId = json(postOrder(customer, orderBody(1, 1001, 1),
                "staff-workbench-key-1")).path("id").asLong();
        assertThat(postNoBody(customer, "/api/orders/" + orderId + "/pay").statusCode())
                .isEqualTo(200);

        BrowserClient staff = newClient();
        login(staff, "demo_staff", "Burger@123");
        HttpResponse<String> list = get(staff, "/api/staff/orders?storeId=1");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json(list).path("items").size()).isEqualTo(1);
        assertThat(json(list).path("items").get(0).path("status").asText()).isEqualTo("WAIT_MAKE");

        assertThat(get(staff, "/api/staff/orders?storeId=2").statusCode()).isEqualTo(404);

        assertThat(postNoBody(staff, "/api/staff/orders/" + orderId + "/start").statusCode())
                .isEqualTo(200);
        assertThat(json(postNoBody(staff, "/api/staff/orders/" + orderId + "/ready")).path("status").asText())
                .isEqualTo("READY");
        assertThat(json(postNoBody(staff, "/api/staff/orders/" + orderId + "/complete")).path("status").asText())
                .isEqualTo("COMPLETED");

        assertThat(json(get(staff, "/api/staff/orders?storeId=1")).path("items").size()).isZero();

        HttpResponse<String> events = get(customer, "/api/orders/" + orderId + "/events");
        assertThat(json(events).size()).isEqualTo(5);
        assertThat(json(events).get(4).path("toStatus").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void illegalTransitionAndWrongRolesAreRejected() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        long orderId = json(postOrder(customer, orderBody(1, 1001, 1),
                "illegal-transition-key-1")).path("id").asLong();

        BrowserClient staff = newClient();
        login(staff, "demo_staff", "Burger@123");
        HttpResponse<String> illegal = postNoBody(
                staff, "/api/staff/orders/" + orderId + "/start");
        assertThat(illegal.statusCode()).isEqualTo(409);
        assertThat(json(illegal).path("code").asText()).isEqualTo("ORDER_STATUS_CONFLICT");

        assertThat(postNoBody(staff, "/api/orders/" + orderId + "/pay").statusCode())
                .isEqualTo(403);

        BrowserClient other = newClient();
        login(other, "demo_customer_two", "Burger@123");
        assertThat(postNoBody(other, "/api/orders/" + orderId + "/pay").statusCode())
                .isEqualTo(404);
    }

    @Test
    void customerCancelsUnpaidOrderAndReleasesReservedStockOnce() throws Exception {
        BrowserClient customer = newClient();
        login(customer, "demo_customer", "Burger@123");
        long orderId = json(postOrder(customer, orderBody(1, 1001, 1),
                "cancel-order-key-0001")).path("id").asLong();
        assertThat(stock("1001")).containsExactly(49, 1);

        HttpResponse<String> cancelled = postNoBody(
                customer, "/api/orders/" + orderId + "/cancel");
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(json(cancelled).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(stock("1001")).containsExactly(50, 0);
        assertThat(count("stock_logs")).isEqualTo(2);
        assertThat(count("order_events")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_logs
                 WHERE order_id = ? AND change_type = 'RELEASE'
                """, Integer.class, orderId)).isEqualTo(1);

        HttpResponse<String> duplicate = postNoBody(
                customer, "/api/orders/" + orderId + "/cancel");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json(duplicate).path("code").asText()).isEqualTo("ORDER_STATUS_CONFLICT");
        assertThat(stock("1001")).containsExactly(50, 0);

        HttpResponse<String> payAfterCancel = postNoBody(
                customer, "/api/orders/" + orderId + "/pay");
        assertThat(payAfterCancel.statusCode()).isEqualTo(409);
        assertThat(json(payAfterCancel).path("code").asText()).isEqualTo("ORDER_STATUS_CONFLICT");
    }

    @Test
    void otherCustomerAndStaffCannotCancelTheOrder() throws Exception {
        BrowserClient owner = newClient();
        login(owner, "demo_customer", "Burger@123");
        long orderId = json(postOrder(owner, orderBody(1, 1001, 1),
                "cancel-ownership-key-1")).path("id").asLong();

        BrowserClient other = newClient();
        login(other, "demo_customer_two", "Burger@123");
        assertThat(postNoBody(other, "/api/orders/" + orderId + "/cancel").statusCode())
                .isEqualTo(404);

        BrowserClient staff = newClient();
        login(staff, "demo_staff", "Burger@123");
        assertThat(postNoBody(staff, "/api/orders/" + orderId + "/cancel").statusCode())
                .isEqualTo(403);
        assertThat(stock("1001")).containsExactly(49, 1);
    }

    @Test
    void pausedStoreCannotCreateOrder() throws Exception {
        BrowserClient client = newClient();
        login(client, "demo_customer", "Burger@123");

        HttpResponse<String> response = postOrder(client, orderBody(2, 2001, 1),
                "order-key-paused-0001");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).path("code").asText()).isEqualTo("STORE_NOT_ORDERABLE");
        assertThat(count("orders")).isZero();
    }

    private long couponTemplateId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM coupon_templates WHERE code = ?", Long.class, code);
    }

    private Map<String, Object> orderBodyWithCoupon(
            long storeId, long userCouponId, int... productQuantityPairs
    ) {
        Map<String, Object> body = new java.util.HashMap<>(
                orderBody(storeId, productQuantityPairs));
        body.put("userCouponId", userCouponId);
        return body;
    }

    private Map<String, Object> orderBody(long storeId, int... productQuantityPairs) {
        if (productQuantityPairs.length % 2 != 0) {
            throw new IllegalArgumentException("product/quantity pairs required");
        }
        var items = new java.util.ArrayList<Map<String, Object>>();
        for (int i = 0; i < productQuantityPairs.length; i += 2) {
            items.add(Map.of(
                    "productId", productQuantityPairs[i],
                    "quantity", productQuantityPairs[i + 1]));
        }
        return Map.of("storeId", storeId, "items", items);
    }

    private HttpResponse<String> postOrder(
            BrowserClient client,
            Map<String, Object> body,
            String idempotencyKey
    ) throws Exception {
        String csrf = fetchCsrf(client);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/orders"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return client.http().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postNoBody(BrowserClient client, String path) throws Exception {
        String csrf = fetchCsrf(client);
        return client.http().send(HttpRequest.newBuilder(uri(path))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private void login(BrowserClient client, String username, String password) throws Exception {
        String csrf = fetchCsrf(client);
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", password));
        HttpResponse<String> response = client.http().send(HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private String fetchCsrf(BrowserClient client) throws Exception {
        HttpResponse<String> response = get(client, "/api/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response).path("token").asText();
    }

    private HttpResponse<String> get(BrowserClient client, String path) throws Exception {
        return client.http().send(HttpRequest.newBuilder(uri(path))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<Integer> stock(String productId) {
        return jdbcTemplate.query("""
                SELECT available, reserved FROM stock WHERE product_id = ?
                """, (rs, rowNum) -> List.of(rs.getInt("available"), rs.getInt("reserved")),
                Long.valueOf(productId)).get(0);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
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
