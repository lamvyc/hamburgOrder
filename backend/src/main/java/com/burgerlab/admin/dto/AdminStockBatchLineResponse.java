package com.burgerlab.admin.dto;

public record AdminStockBatchLineResponse(
        long productId,
        int delta,
        String reason,
        int availableBefore,
        int availableAfter,
        int stockVersionAfter
) {
}
