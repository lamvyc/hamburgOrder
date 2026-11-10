package com.burgerlab.store.dto;

import java.util.List;

public record StoreListResponse(List<StoreResponse> items) {

    public StoreListResponse {
        items = List.copyOf(items);
    }
}
