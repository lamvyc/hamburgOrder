package com.burgerlab.admin;

import com.burgerlab.admin.dto.AdminStockBatchResponse;

public record AdminStockBatchResult(AdminStockBatchResponse response, boolean replayed) {
}
