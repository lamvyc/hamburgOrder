package com.burgerlab.coupon.dto;

import java.time.OffsetDateTime;

public record UserCouponResponse(
        long id,
        long templateId,
        String code,
        String name,
        String discountAmount,
        String minOrderAmount,
        String status,
        Long orderId,
        OffsetDateTime claimedAt,
        OffsetDateTime usedAt
) {
}
