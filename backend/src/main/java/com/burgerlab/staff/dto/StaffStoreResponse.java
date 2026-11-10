package com.burgerlab.staff.dto;

import com.burgerlab.store.StoreBusinessStatus;

public record StaffStoreResponse(
        long id,
        String code,
        String name,
        String address,
        StoreBusinessStatus businessStatus,
        boolean acceptingOrders,
        boolean orderable
) {
}
