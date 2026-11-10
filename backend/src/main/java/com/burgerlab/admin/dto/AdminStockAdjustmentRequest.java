package com.burgerlab.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminStockAdjustmentRequest(
        @NotNull(message = "调整量不能为空")
        Integer delta,

        @NotNull(message = "版本号不能为空")
        @PositiveOrZero(message = "版本号不能为负")
        Integer version,

        @NotBlank(message = "调整原因不能为空")
        @Size(max = 200, message = "调整原因最多 200 字")
        String reason
) {
}
