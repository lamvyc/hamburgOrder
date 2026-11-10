package com.burgerlab.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.auth.UserRole;
import com.burgerlab.common.error.BusinessConflictException;
import com.burgerlab.order.dto.CreateOrderItemRequest;
import com.burgerlab.order.dto.CreateOrderRequest;
import java.util.List;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Concurrency evidence at the service/transaction boundary. Both requests are
 * released by the same barrier, and assertions are about the final database
 * state, not a lucky single success.
 */
@SpringBootTest(properties = {"app.order.timeout-scan.enabled=false", "app.cache.menu.enabled=false"})
@Testcontainers
class OrderConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.13")
            .withDatabaseName("burger_order")
            .withUsername("burger_test")
            .withPassword("burger_test_password");

    private static final AppUserPrincipal CUSTOMER = new AppUserPrincipal(
            1L, "demo_customer", "unused", "演示顾客", UserRole.CUSTOMER, true);

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderCancellationService cancellationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM stock_logs");
        jdbcTemplate.update("DELETE FROM order_events");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM idempotency_records");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("""
                UPDATE stock
                   SET available = 10, reserved = 0, version = 0
                 WHERE product_id = 1001
                """);
    }

    @Test
    void twoConcurrentRequestsWithTheSameKeyCreateOnlyOneOrder() throws Exception {
        CreateOrderRequest request = request(1001, 2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CreateOrderResult> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.create(request, "same-key-concurrency-01", CUSTOMER);
            });
            Future<CreateOrderResult> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.create(request, "same-key-concurrency-01", CUSTOMER);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            CreateOrderResult a = first.get(15, TimeUnit.SECONDS);
            CreateOrderResult b = second.get(15, TimeUnit.SECONDS);

            assertThat(a.order().id()).isEqualTo(b.order().id());
            assertThat(List.of(a.replayed(), b.replayed())).containsExactlyInAnyOrder(false, true);
            assertThat(count("orders")).isEqualTo(1);
            assertThat(count("order_items")).isEqualTo(1);
            assertThat(count("stock_logs")).isEqualTo(1);
            assertThat(count("idempotency_records")).isEqualTo(1);
            assertThat(stock(1001)).containsExactly(8, 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void twoDifferentKeysForTheLastUnitAllowExactlyOneOrder() throws Exception {
        jdbcTemplate.update("UPDATE stock SET available = 1 WHERE product_id = 1001");
        CreateOrderRequest request = request(1001, 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CreateOrderResult> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.create(request, "last-unit-key-0001", CUSTOMER);
            });
            Future<CreateOrderResult> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.create(request, "last-unit-key-0002", CUSTOMER);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            int stockFailures = 0;
            for (Future<CreateOrderResult> future : List.of(first, second)) {
                try {
                    future.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(BusinessConflictException.class);
                    BusinessConflictException conflict = (BusinessConflictException) exception.getCause();
                    assertThat(conflict.code()).isEqualTo("STOCK_INSUFFICIENT");
                    stockFailures++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(stockFailures).isEqualTo(1);
            assertThat(count("orders")).isEqualTo(1);
            assertThat(count("order_items")).isEqualTo(1);
            assertThat(count("stock_logs")).isEqualTo(1);
            assertThat(count("idempotency_records")).isEqualTo(1);
            assertThat(stock(1001)).containsExactly(0, 1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void twoConcurrentPaymentsCommitTheReservationOnlyOnce() throws Exception {
        CreateOrderResult created = orderService.create(
                request(1001, 1), "payment-concurrency-create", CUSTOMER);
        long orderId = created.order().id();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.payOrder(orderId, CUSTOMER);
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.payOrder(orderId, CUSTOMER);
            });

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
                            .isEqualTo("ORDER_STATUS_CONFLICT");
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(stock(1001)).containsExactly(9, 0);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM stock_logs WHERE change_type = 'COMMIT'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM order_events WHERE event_type = 'PAYMENT_SUCCEEDED'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, orderId))
                    .isEqualTo("WAIT_MAKE");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void paymentAndCustomerCancelRaceHasOneConsistentWinner() throws Exception {
        CreateOrderResult created = orderService.create(
                request(1001, 1), "payment-cancel-race-key-1", CUSTOMER);
        long orderId = created.order().id();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> pay = executor.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.payOrder(orderId, CUSTOMER);
            });
            Future<?> cancel = executor.submit(() -> {
                ready.countDown();
                start.await();
                return cancellationService.cancelByCustomer(orderId, CUSTOMER);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<?> future : List.of(pay, cancel)) {
                try {
                    future.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(BusinessConflictException.class);
                    assertThat(((BusinessConflictException) exception.getCause()).code())
                            .isEqualTo("ORDER_STATUS_CONFLICT");
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, orderId);
            List<Integer> stock = jdbcTemplate.queryForObject("""
                    SELECT available, reserved FROM stock WHERE product_id = 1001
                    """, (rs, rowNum) -> List.of(rs.getInt("available"), rs.getInt("reserved")));

            if ("WAIT_MAKE".equals(status)) {
                assertThat(stock).containsExactly(9, 0);
                assertThat(countTerminalLogs("COMMIT")).isEqualTo(1);
                assertThat(countTerminalLogs("RELEASE")).isZero();
                assertThat(countEvent("PAYMENT_SUCCEEDED")).isEqualTo(1);
                assertThat(countEvent("CANCELLED")).isZero();
            } else {
                assertThat(status).isEqualTo("CANCELLED");
                assertThat(stock).containsExactly(10, 0);
                assertThat(countTerminalLogs("COMMIT")).isZero();
                assertThat(countTerminalLogs("RELEASE")).isEqualTo(1);
                assertThat(countEvent("PAYMENT_SUCCEEDED")).isZero();
                assertThat(countEvent("CANCELLED")).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private CreateOrderRequest request(long productId, int quantity) {
        return new CreateOrderRequest(
                1L,
                List.of(new CreateOrderItemRequest(productId, quantity))
        );
    }

    private int countTerminalLogs(String changeType) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_logs WHERE change_type = ?
                """, Integer.class, changeType);
    }

    private int countEvent(String eventType) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM order_events WHERE event_type = ?
                """, Integer.class, eventType);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private List<Integer> stock(long productId) {
        return jdbcTemplate.queryForObject("""
                SELECT available, reserved FROM stock WHERE product_id = ?
                """, (rs, rowNum) -> List.of(rs.getInt("available"), rs.getInt("reserved")),
                productId);
    }
}
