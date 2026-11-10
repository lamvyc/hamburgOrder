package com.burgerlab.order;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.order.timeout-scan.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final OrderTimeoutService timeoutService;
    private final int batchSize;

    public OrderTimeoutScheduler(
            OrderTimeoutService timeoutService,
            @Value("${app.order.timeout-scan.batch-size:100}") int batchSize
    ) {
        this.timeoutService = timeoutService;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.order.timeout-scan.fixed-delay-ms:60000}",
            initialDelayString = "${app.order.timeout-scan.initial-delay-ms:30000}"
    )
    public void cancelExpiredOrders() {
        int cancelled = timeoutService.cancelExpiredBatch(
                LocalDateTime.now(ZoneOffset.UTC), batchSize);
        if (cancelled > 0) {
            log.info("timeout_cancel_batch cancelled={}", cancelled);
        }
    }
}
