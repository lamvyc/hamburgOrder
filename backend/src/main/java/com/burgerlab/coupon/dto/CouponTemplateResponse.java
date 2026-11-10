package com.burgerlab.coupon.dto;

import java.time.OffsetDateTime;

public record CouponTemplateResponse(
        long id,
        String code,
        String name,
        String discountAmount,
        String minOrderAmount,
        int remainingQuantity,
        boolean claimed,
        OffsetDateTime validFrom,
        OffsetDateTime validTo
) {
}
