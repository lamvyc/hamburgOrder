package com.burgerlab.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.auth.UserRole;
import com.burgerlab.common.error.BusinessConflictException;
import com.burgerlab.order.OrderService;
import com.burgerlab.order.dto.CreateOrderItemRequest;
import com.burgerlab.order.dto.CreateOrderRequest;
import com.burgerlab.coupon.dto.UserCouponResponse;
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

@SpringBootTest(properties = {"app.order.timeout-scan.enabled=false", "app.cache.menu.enabled=false"})
@Testcontainers
class CouponConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.13")
            .withDatabaseName("burger_order")
            .withUsername("burger_test")
            .withPassword("burger_test_password");

    private static final AppUserPrincipal CUSTOMER = new AppUserPrincipal(
            1L, "demo_customer", "unused", "演示顾客", UserRole.CUSTOMER, true);
    private static final AppUserPrincipal OTHER = new AppUserPrincipal(
            4L, "demo_customer_two", "unused", "第二位顾客", UserRole.CUSTOMER, true);

    @Autowired
    private CouponService couponService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM user_coupons");
        jdbcTemplate.update("DELETE FROM stock_logs");
        jdbcTemplate.update("DELETE FROM order_events");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM idempotency_records");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("""
                UPDATE stock SET available = 50, reserved = 0, version = 0
                 WHERE product_id = 1001
                """);
        jdbcTemplate.update("""
                UPDATE coupon_templates
                   SET claimed_quantity = 0, status = 'ACTIVE', version = 0,
                       total_quantity = CASE code WHEN 'WELCOME5' THEN 100 ELSE 50 END
                """);
    }

    @Test
    void lastCouponUnitGoesToExactlyOneOfTwoUsers() throws Exception {
        long templateId = templateId("WELCOME5");
        jdbcTemplate.update("""
                UPDATE coupon_templates SET total_quantity = 1 WHERE id = ?
                """, templateId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> claimAfterBarrier(templateId, CUSTOMER, ready, start));
            Future<?> second = executor.submit(() -> claimAfterBarrier(templateId, OTHER, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successes = 0;
            int soldOut = 0;
            for (Future<?> future : List.of(first, second)) {
                try {
                    future.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(BusinessConflictException.class);
                    assertThat(((BusinessConflictException) exception.getCause()).code())
                            .isEqualTo("COUPON_SOLD_OUT");
                    soldOut++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(soldOut).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT claimed_quantity FROM coupon_templates WHERE id = ?
                    """, Integer.class, templateId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_coupons", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameUserConcurrentClaimsCreateExactlyOneCoupon() throws Exception {
        long templateId = templateId("WELCOME5");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> claimAfterBarrier(templateId, CUSTOMER, ready, start));
            Future<?> second = executor.submit(() -> claimAfterBarrier(templateId, CUSTOMER, ready, start));
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
                            .isEqualTo("COUPON_ALREADY_CLAIMED");
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT claimed_quantity FROM coupon_templates WHERE id = ?
                    """, Integer.class, templateId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_coupons", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void oneCouponCannotLockTwoConcurrentOrders() throws Exception {
        long templateId = templateId("WELCOME5");
        UserCouponResponse coupon = couponService.claim(templateId, CUSTOMER.id());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> createOrderAfterBarrier(
                    coupon.id(), "coupon-race-order-1", ready, start));
            Future<?> second = executor.submit(() -> createOrderAfterBarrier(
                    coupon.id(), "coupon-race-order-2", ready, start));
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
                            .isEqualTo("COUPON_NOT_USABLE");
                    conflicts++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT status FROM user_coupons WHERE id = ?
                    """, String.class, coupon.id())).isEqualTo("LOCKED");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT discount_amount FROM orders LIMIT 1
                    """, java.math.BigDecimal.class)).isEqualByComparingTo("5.00");
        } finally {
            executor.shutdownNow();
        }
    }

    private UserCouponResponse claimAfterBarrier(
            long templateId,
            AppUserPrincipal principal,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return couponService.claim(templateId, principal.id());
    }

    private Object createOrderAfterBarrier(
            long couponId,
            String key,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return orderService.create(new CreateOrderRequest(
                1L,
                List.of(new CreateOrderItemRequest(1001L, 2)),
                couponId
        ), key, CUSTOMER);
    }

    private long templateId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM coupon_templates WHERE code = ?", Long.class, code);
    }
}
