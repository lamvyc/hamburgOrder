package com.burgerlab.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "门店 ID 不能为空")
        @Positive(message = "门店 ID 必须为正数")
        Long storeId,

        @Valid
        @NotEmpty(message = "订单至少包含一个商品")
        @Size(max = 20, message = "单笔订单最多包含 20 种商品")
        List<CreateOrderItemRequest> items,

        @Positive(message = "优惠券 ID 必须为正数")
        Long userCouponId
) {

    public CreateOrderRequest(Long storeId, List<CreateOrderItemRequest> items) {
        this(storeId, items, null);
    }
}
