package com.burgerlab.admin.dto;

import java.util.List;

public record AdminProductPageResponse(
        List<AdminProductResponse> items,
        Long nextCursor,
        boolean hasMore
) {
    public AdminProductPageResponse {
        items = List.copyOf(items);
    }
}
