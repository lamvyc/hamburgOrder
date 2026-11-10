package com.burgerlab.store.dto;

import com.burgerlab.store.StoreBusinessStatus;
import java.math.BigDecimal;

public record StoreResponse(
        long id,
        String code,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        StoreBusinessStatus businessStatus,
        boolean acceptingOrders,
        boolean orderable,
        String openingHours
) {
}
