package com.burgerlab.catalog.dto;

import java.util.List;

public record MenuCategoryResponse(
        String code,
        String name,
        List<MenuItemResponse> products
) {

    public MenuCategoryResponse {
        products = List.copyOf(products);
    }
}
