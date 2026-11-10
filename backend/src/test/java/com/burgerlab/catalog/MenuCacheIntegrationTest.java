package com.burgerlab.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.order.timeout-scan.enabled=false"
)
@Testcontainers
class MenuCacheIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.13")
            .withDatabaseName("burger_order")
            .withUsername("burger_test")
            .withPassword("burger_test_password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void resetState() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        jdbcTemplate.update("""
                UPDATE products SET price = 32.00, on_sale = 1, version = 0 WHERE id = 1001
                """);
    }

    @Test
    void firstMenuRequestMissesAndSecondHits() throws Exception {
        BrowserClient client = newClient();

        HttpResponse<String> first = get(client, "/api/stores/1/menu");
        HttpResponse<String> second = get(client, "/api/stores/1/menu");

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("X-Cache")).contains("MISS");
        assertThat(second.headers().firstValue("X-Cache")).contains("HIT");
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(redis.getExpire("menu:v1:1", TimeUnit.SECONDS)).isGreaterThan(0);
        assertThat(redis.getExpire("menu:v1:1", TimeUnit.SECONDS)).isLessThanOrEqualTo(75);
    }

    @Test
    void missingStoreIsNegativelyCached() throws Exception {
        BrowserClient client = newClient();

        HttpResponse<String> first = get(client, "/api/stores/999/menu");
        HttpResponse<String> second = get(client, "/api/stores/999/menu");

        assertThat(first.statusCode()).isEqualTo(404);
        assertThat(second.statusCode()).isEqualTo(404);
        assertThat(redis.opsForValue().get("menu:v1:999")).isEqualTo("__MISSING__");
        assertThat(redis.getExpire("menu:v1:999", TimeUnit.SECONDS)).isBetween(1L, 10L);
    }

    @Test
    void cacheStatsEndpointReportsCounters() throws Exception {
        BrowserClient client = newClient();
        get(client, "/api/stores/1/menu");
        get(client, "/api/stores/1/menu");

        HttpResponse<String> stats = get(client, "/api/system/menu-cache");

        assertThat(stats.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(stats.body()).path("hit").asLong()).isGreaterThan(0);
        assertThat(objectMapper.readTree(stats.body()).path("miss").asLong()).isGreaterThan(0);
    }

    @Test
    void prometheusExposesCacheCounters() throws Exception {
        BrowserClient client = newClient();
        get(client, "/api/stores/1/menu");
        get(client, "/api/stores/1/menu");

        HttpResponse<String> metrics = client.http().send(HttpRequest.newBuilder(
                        uri("/actuator/prometheus"))
                .header("Accept", "text/plain")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("burger_menu_cache_requests_total");
        assertThat(metrics.body()).contains("result=\"hit\"");
        assertThat(metrics.body()).contains("result=\"miss\"");
    }

    @Test
    void adminProductUpdateEvictsMenuCache() throws Exception {
        BrowserClient admin = newClient();
        login(admin, "demo_admin", "Burger@123");

        assertThat(get(admin, "/api/stores/1/menu").headers().firstValue("X-Cache"))
                .contains("MISS");

        String csrf = fetchCsrf(admin);
        HttpResponse<String> updated = admin.http().send(HttpRequest.newBuilder(
                        uri("/api/admin/products/1001"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(Map.of(
                                "price", "33.50",
                                "onSale", false,
                                "version", 0))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(updated.statusCode()).isEqualTo(200);

        HttpResponse<String> after = get(admin, "/api/stores/1/menu");
        assertThat(after.headers().firstValue("X-Cache")).contains("MISS");
        assertThat(after.body()).doesNotContain("S1-CLASSIC");
    }

    private HttpResponse<String> get(BrowserClient client, String path) throws Exception {
        return client.http().send(HttpRequest.newBuilder(uri(path))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private void login(BrowserClient client, String username, String password) throws Exception {
        String csrf = fetchCsrf(client);
        HttpResponse<String> response = client.http().send(HttpRequest.newBuilder(
                        uri("/api/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-XSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        Map.of("username", username, "password", password))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private String fetchCsrf(BrowserClient client) throws Exception {
        HttpResponse<String> response = get(client, "/api/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body()).path("token").asText();
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
