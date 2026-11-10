package com.burgerlab.store;

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
 * Store/menu read model against a real migrated MySQL database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"app.order.timeout-scan.enabled=false", "app.cache.menu.enabled=false"})
@Testcontainers
class StoreCatalogIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.13")
            .withDatabaseName("burger_order")
            .withUsername("burger_test")
            .withPassword("burger_test_password");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void listsAllStoresWithIndependentBusinessAndAcceptingState() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity("/api/stores", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("items").size()).isEqualTo(3);

        JsonNode central = findStore(body.path("items"), 1);
        assertThat(central.path("name").asText()).isEqualTo("中央车站店");
        assertThat(central.path("businessStatus").asText()).isEqualTo("OPEN");
        assertThat(central.path("acceptingOrders").asBoolean()).isTrue();
        assertThat(central.path("orderable").asBoolean()).isTrue();
        assertThat(central.path("latitude").asDouble()).isEqualTo(31.2304);
        assertThat(central.path("longitude").asDouble()).isEqualTo(121.4737);

        JsonNode riverside = findStore(body.path("items"), 2);
        assertThat(riverside.path("businessStatus").asText()).isEqualTo("OPEN");
        assertThat(riverside.path("acceptingOrders").asBoolean()).isFalse();
        assertThat(riverside.path("orderable").asBoolean()).isFalse();

        JsonNode oldTown = findStore(body.path("items"), 3);
        assertThat(oldTown.path("businessStatus").asText()).isEqualTo("CLOSED");
        assertThat(oldTown.path("acceptingOrders").asBoolean()).isFalse();
        assertThat(oldTown.path("orderable").asBoolean()).isFalse();
    }

    @Test
    void returnsOneStoreAndNotFoundForMissingStore() {
        ResponseEntity<JsonNode> found = restTemplate.getForEntity("/api/stores/1", JsonNode.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).isNotNull();
        assertThat(found.getBody().path("name").asText()).isEqualTo("中央车站店");

        ResponseEntity<JsonNode> missing = restTemplate.getForEntity("/api/stores/999", JsonNode.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().path("code").asText()).isEqualTo("STORE_NOT_FOUND");
        assertThat(missing.getBody().path("traceId").asText()).isNotBlank();
    }

    @Test
    void menuIsGroupedAndOnlyContainsOnSaleProducts() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity("/api/stores/1/menu", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("store").path("name").asText()).isEqualTo("中央车站店");

        JsonNode categories = body.path("categories");
        assertThat(categories.size()).isEqualTo(3);
        assertThat(categories.get(0).path("code").asText()).isEqualTo("BURGER");
        assertThat(categories.get(0).path("name").asText()).isEqualTo("汉堡");
        assertThat(categories.get(0).path("products").size()).isEqualTo(3);
        assertThat(categories.get(1).path("code").asText()).isEqualTo("SIDE");
        assertThat(categories.get(2).path("code").asText()).isEqualTo("DRINK");
        assertThat(categories.get(2).path("products").size()).isEqualTo(1);

        JsonNode classic = categories.get(0).path("products").get(0);
        assertThat(classic.path("name").asText()).isEqualTo("经典牛肉堡");
        assertThat(classic.path("price").asText()).isEqualTo("32.00");

        boolean containsOffSaleShake = categories.get(2).path("products").findValuesAsText("name")
                .contains("海盐奶昔");
        assertThat(containsOffSaleShake).isFalse();
    }

    @Test
    void closedStoreStillAllowsMenuBrowsingButIsNotOrderable() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity("/api/stores/3/menu", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("store").path("businessStatus").asText()).isEqualTo("CLOSED");
        assertThat(body.path("store").path("orderable").asBoolean()).isFalse();
        assertThat(body.path("categories").size()).isEqualTo(3);
    }

    @Test
    void invalidStoreIdIsABadRequestAndMissingMenuIsNotFound() {
        ResponseEntity<JsonNode> invalid = restTemplate.getForEntity("/api/stores/abc", JsonNode.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).isNotNull();
        assertThat(invalid.getBody().path("code").asText()).isEqualTo("INVALID_PARAMETER");

        ResponseEntity<JsonNode> missingMenu = restTemplate.getForEntity(
                "/api/stores/999/menu", JsonNode.class);
        assertThat(missingMenu.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingMenu.getBody()).isNotNull();
        assertThat(missingMenu.getBody().path("code").asText()).isEqualTo("STORE_NOT_FOUND");
    }

    private JsonNode findStore(JsonNode stores, long id) {
        for (JsonNode store : stores) {
            if (store.path("id").asLong() == id) {
                return store;
            }
        }
        throw new AssertionError("Store not found in response: " + id);
    }
}
