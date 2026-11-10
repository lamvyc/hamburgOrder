package com.burgerlab.catalog.dto;

import com.burgerlab.store.dto.StoreResponse;
import java.util.List;

public record MenuResponse(
        StoreResponse store,
        List<MenuCategoryResponse> categories
) {

    public MenuResponse {
        categories = List.copyOf(categories);
    }
}
