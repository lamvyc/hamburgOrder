package com.burgerlab.order.dto;

import com.burgerlab.order.OrderStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        long id,
        String orderNo,
        OrderStatus status,
        long storeId,
        String storeName,
        String totalAmount,
        String discountAmount,
        String currency,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        List<OrderItemResponse> items
) {

    public OrderResponse {
        items = List.copyOf(items);
    }
}
