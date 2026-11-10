package com.burgerlab.admin.dto;

import java.util.List;

public record AdminStockPreviewResponse(
        boolean valid,
        int errorCount,
        List<AdminStockPreviewLineResponse> lines
) {
    public AdminStockPreviewResponse {
        lines = List.copyOf(lines);
    }
}
