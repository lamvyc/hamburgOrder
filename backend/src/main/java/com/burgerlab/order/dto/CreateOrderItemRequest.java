package com.burgerlab.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderItemRequest(
        @NotNull(message = "商品 ID 不能为空")
        @Positive(message = "商品 ID 必须为正数")
        Long productId,

        @NotNull(message = "商品数量不能为空")
        @Min(value = 1, message = "商品数量至少为 1")
        @Max(value = 99, message = "商品数量不能超过 99")
        Integer quantity
) {
}
