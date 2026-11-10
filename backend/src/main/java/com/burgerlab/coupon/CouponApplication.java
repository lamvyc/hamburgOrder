package com.burgerlab.coupon;

import java.math.BigDecimal;

public record CouponApplication(long userCouponId, BigDecimal discountAmount) {
}
