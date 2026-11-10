package com.burgerlab.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.auth.UserRole;
import com.burgerlab.common.error.BusinessConflictException;
import com.burgerlab.order.dto.CreateOrderItemRequest;
import com.burgerlab.order.dto.CreateOrderRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"app.order.timeout-scan.enabled=false", "app.cache.menu.enabled=false"})
@Testcontainers
class OrderTimeoutIntegrationTest {

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
    private OrderTimeoutService timeoutService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM stock_logs");
        jdbcTemplate.update("DELETE FROM order_events");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM idempotency_records");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("UPDATE stock SET available = 10, reserved = 0, version = 0 WHERE product_id = 1001");
    }

    @Test
    void expiredOrderIsCancelledAndStockReleasedExactlyOnce() {
        long orderId = createOrder("timeout-order-key-0001");
        jdbcTemplate.update("""
                UPDATE orders SET expires_at = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE)
                 WHERE id = ?
                """, orderId);

        int first = timeoutService.cancelExpiredBatch(LocalDateTime.now(ZoneOffset.UTC), 20);
        int second = timeoutService.cancelExpiredBatch(LocalDateTime.now(ZoneOffset.UTC), 20);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("CANCELLED");
        assertThat(stock()).containsExactly(10, 0);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM stock_logs WHERE order_id = ? AND change_type = 'RELEASE'
                """, Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM order_events
                 WHERE order_id = ? AND event_type = 'CANCELLED' AND operator_user_id IS NULL
                """, Integer.class, orderId)).isEqualTo(1);

        assertThatThrownBy(() -> orderService.payOrder(orderId, CUSTOMER))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("ORDER_STATUS_CONFLICT");
    }

    @Test
    void activeOrderIsNotTouchedByTimeoutScan() {
        long orderId = createOrder("timeout-active-key-0001");

        int cancelled = timeoutService.cancelExpiredBatch(LocalDateTime.now(ZoneOffset.UTC), 20);

        assertThat(cancelled).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("WAIT_PAY");
        assertThat(stock()).containsExactly(9, 1);
    }

    private long createOrder(String idempotencyKey) {
        return orderService.create(new CreateOrderRequest(
                1L,
                List.of(new CreateOrderItemRequest(1001L, 1))
        ), idempotencyKey, CUSTOMER).order().id();
    }

    private List<Integer> stock() {
        return jdbcTemplate.queryForObject("""
                SELECT available, reserved FROM stock WHERE product_id = 1001
                """, (rs, rowNum) -> List.of(rs.getInt("available"), rs.getInt("reserved")));
    }
}
