package com.burgerlab.admin.dto;

import java.util.List;

public record AdminStockBatchResponse(
        long batchId,
        int appliedCount,
        List<AdminStockBatchLineResponse> lines
) {
    public AdminStockBatchResponse {
        lines = List.copyOf(lines);
    }
}
