package com.burgerlab.order;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);

    private final OrderMapper orderMapper;
    private final OrderCancellationService cancellationService;

    public OrderTimeoutService(
            OrderMapper orderMapper,
            OrderCancellationService cancellationService
    ) {
        this.orderMapper = orderMapper;
        this.cancellationService = cancellationService;
    }

    public int cancelExpiredBatch(LocalDateTime cutoff, int limit) {
        List<Long> ids = orderMapper.selectExpiredIds(cutoff, limit);
        int cancelled = 0;
        for (Long orderId : ids) {
            try {
                if (cancellationService.cancelExpired(orderId, cutoff)) {
                    cancelled++;
                }
            } catch (Exception exception) {
                log.error("timeout_cancel_failed orderId={} error={}",
                        orderId, exception.getClass().getSimpleName());
            }
        }
        return cancelled;
    }
}
