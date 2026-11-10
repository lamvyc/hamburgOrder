package com.burgerlab.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record AdminProductUpdateRequest(
        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0.01", message = "价格必须大于 0")
        BigDecimal price,

        @NotNull(message = "上架状态不能为空")
        Boolean onSale,

        @NotNull(message = "版本号不能为空")
        @PositiveOrZero(message = "版本号不能为负")
        Integer version
) {
}
