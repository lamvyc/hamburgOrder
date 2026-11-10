package com.burgerlab.admin.dto;

public record AdminStockPreviewLineResponse(
        int lineNo,
        Long productId,
        Integer delta,
        String reason,
        boolean valid,
        String code,
        String message,
        Integer availableBefore,
        Integer availableAfter
) {
}
