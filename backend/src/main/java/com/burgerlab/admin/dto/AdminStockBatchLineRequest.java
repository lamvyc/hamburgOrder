package com.burgerlab.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminStockBatchLineRequest(
        @NotNull @Positive Long productId,
        @NotNull Integer delta,
        @NotBlank @Size(max = 200) String reason
) {
}
