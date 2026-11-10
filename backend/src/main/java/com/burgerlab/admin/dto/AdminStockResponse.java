package com.burgerlab.admin.dto;

public record AdminStockResponse(
        long productId,
        int available,
        int reserved,
        int version
) {
}
