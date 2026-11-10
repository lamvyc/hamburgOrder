package com.burgerlab.platform.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the real path: HTTP -> service -> MyBatis -> MySQL, with Flyway
 * applying the migration first. This is intentionally not a mocked test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"app.order.timeout-scan.enabled=false", "app.cache.menu.enabled=false"})
@Testcontainers
class PlatformHealthIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.13")
            .withDatabaseName("burger_order")
            .withUsername("burger_test")
            .withPassword("burger_test_password");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void livenessIsAvailableWithoutReportingDatabaseHealth() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/system/liveness", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
        assertThat(response.getBody().path("service").asText()).isEqualTo("burger-order-api");
        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isNotBlank();
    }

    @Test
    void statusReadsTheMigratedMarkerAndFlywayHistory() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/system/status", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.path("application").path("status").asText()).isEqualTo("UP");
        assertThat(body.path("database").path("status").asText()).isEqualTo("UP");
        assertThat(body.path("database").path("connected").asBoolean()).isTrue();
        assertThat(body.path("database").path("schemaReady").asBoolean()).isTrue();
        assertThat(body.path("database").path("product").asText()).isEqualTo("MySQL");
        assertThat(body.path("database").path("version").asText()).startsWith("8.4.");
        assertThat(body.path("database").path("flywayVersion").asText()).isEqualTo("13");
        assertThat(body.path("database").path("lastMigration").asText())
                .contains("create admin stock batches");
        assertThat(body.path("traceId").asText()).isNotBlank();
    }

    @Test
    void actuatorSeparatesLivenessAndReadiness() {
        ResponseEntity<JsonNode> liveness = restTemplate.getForEntity(
                "/actuator/health/liveness", JsonNode.class);
        ResponseEntity<JsonNode> readiness = restTemplate.getForEntity(
                "/actuator/health/readiness", JsonNode.class);

        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getBody()).isNotNull();
        assertThat(liveness.getBody().path("status").asText()).isEqualTo("UP");

        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).isNotNull();
        assertThat(readiness.getBody().path("status").asText()).isEqualTo("UP");
    }
}
