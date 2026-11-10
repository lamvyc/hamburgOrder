package com.burgerlab.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminStockBatchRequest(
        @Valid
        @NotEmpty
        @Size(max = 100)
        List<AdminStockBatchLineRequest> lines
) {
}
