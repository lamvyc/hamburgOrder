package com.burgerlab.order.dto;

public record OrderItemResponse(
        long productId,
        String productCode,
        String productName,
        String unitPrice,
        int quantity,
        String lineAmount
) {
}
