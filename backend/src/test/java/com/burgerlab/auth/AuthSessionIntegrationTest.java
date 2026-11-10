package com.burgerlab.auth;

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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises real servlet sessions, CSRF cookies and role checks against a real
 * MySQL database. This intentionally avoids mock security contexts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"app.order.timeout-scan.enabled=false", "app.cache.menu.enabled=false"})
@Testcontainers
class AuthSessionIntegrationTest {

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

    @Test
    void unauthenticatedMeIsA401JsonError() throws Exception {
        BrowserClient client = newClient();
        HttpResponse<String> response = get(client, "/api/auth/me");

        assertThat(response.statusCode()).isEqualTo(401);
        JsonNode body = json(response);
        assertThat(body.path("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.path("traceId").asText()).isNotBlank();
    }

    @Test
    void customerCanLoginReadOwnSessionAndCannotUseStaffOrAdminEntries() throws Exception {
        BrowserClient client = newClient();

        HttpResponse<String> login = login(client, "demo_customer", "Burger@123");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues(HttpHeaders.SET_COOKIE).toString())
                .contains("BURGER_SESSION=")
                .contains("HttpOnly")
                .containsIgnoringCase("SameSite=Lax");
        assertThat(json(login).path("role").asText()).isEqualTo("CUSTOMER");

        HttpResponse<String> me = get(client, "/api/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json(me).path("username").asText()).isEqualTo("demo_customer");
        assertThat(json(me).path("role").asText()).isEqualTo("CUSTOMER");

        assertThat(get(client, "/api/staff/workspace").statusCode()).isEqualTo(403);
        assertThat(get(client, "/api/admin/entry").statusCode()).isEqualTo(403);
    }

    @Test
    void staffSeesOnlyAssignedStoresAndCannotUseAdminEntry() throws Exception {
        BrowserClient client = newClient();
        assertThat(login(client, "demo_staff", "Burger@123").statusCode()).isEqualTo(200);

        HttpResponse<String> workspace = get(client, "/api/staff/workspace");
        assertThat(workspace.statusCode()).isEqualTo(200);
        JsonNode body = json(workspace);
        assertThat(body.path("stores").size()).isEqualTo(1);
        assertThat(body.path("stores").get(0).path("id").asLong()).isEqualTo(1);
        assertThat(body.path("stores").get(0).path("name").asText()).isEqualTo("中央车站店");

        assertThat(get(client, "/api/admin/entry").statusCode()).isEqualTo(403);
    }

    @Test
    void adminCanOpenBaseEntryButCannotUseStaffWorkspace() throws Exception {
        BrowserClient client = newClient();
        assertThat(login(client, "demo_admin", "Burger@123").statusCode()).isEqualTo(200);

        HttpResponse<String> entry = get(client, "/api/admin/entry");
        assertThat(entry.statusCode()).isEqualTo(200);
        assertThat(json(entry).path("role").asText()).isEqualTo("ADMIN");
        assertThat(json(entry).path("available").asBoolean()).isTrue();

        assertThat(get(client, "/api/staff/workspace").statusCode()).isEqualTo(403);
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        BrowserClient client = newClient();
        assertThat(login(client, "demo_customer", "Burger@123").statusCode()).isEqualTo(200);
        assertThat(get(client, "/api/auth/me").statusCode()).isEqualTo(200);

        String csrf = fetchCsrf(client);
        HttpResponse<String> logout = post(client, "/api/auth/logout", "{}", csrf);
        assertThat(logout.statusCode()).isEqualTo(204);

        assertThat(get(client, "/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(get(client, "/api/staff/workspace").statusCode()).isEqualTo(401);
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        BrowserClient client = newClient();
        HttpResponse<String> response = post(client, "/api/auth/login",
                objectMapper.writeValueAsString(Map.of(
                        "username", "demo_customer",
                        "password", "Burger@123")), null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(json(response).path("code").asText()).isEqualTo("CSRF_INVALID");
    }

    @Test
    void badCredentialsAreA401WithNoPasswordLeak() throws Exception {
        BrowserClient client = newClient();
        HttpResponse<String> response = login(client, "demo_customer", "wrong-password");

        assertThat(response.statusCode()).isEqualTo(401);
        JsonNode body = json(response);
        assertThat(body.path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body.path("message").asText()).doesNotContain("wrong-password");
        assertThat(body.path("traceId").asText()).isNotBlank();
    }

    private HttpResponse<String> login(BrowserClient client, String username, String password)
            throws Exception {
        String csrf = fetchCsrf(client);
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", password));
        return post(client, "/api/auth/login", body, csrf);
    }

    private String fetchCsrf(BrowserClient client) throws Exception {
        HttpResponse<String> response = get(client, "/api/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        String token = json(response).path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private HttpResponse<String> post(
            BrowserClient client,
            String path,
            String jsonBody,
            String csrfToken
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        return client.http().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(BrowserClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build();
        return client.http().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private BrowserClient newClient() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new BrowserClient(httpClient);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private JsonNode json(HttpResponse<String> response) throws IOException {
        return objectMapper.readTree(response.body());
    }

    private record BrowserClient(HttpClient http) {
    }
}
