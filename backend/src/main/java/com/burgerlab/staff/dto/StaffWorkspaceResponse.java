package com.burgerlab.staff.dto;

import java.util.List;

public record StaffWorkspaceResponse(List<StaffStoreResponse> stores) {

    public StaffWorkspaceResponse {
        stores = List.copyOf(stores);
    }
}
