package com.burgerlab.order.dto;

import com.burgerlab.order.OrderStatus;
import java.time.OffsetDateTime;

public record OrderEventResponse(
        long id,
        String eventType,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String operatorName,
        String reason,
        OffsetDateTime createdAt
) {
}
