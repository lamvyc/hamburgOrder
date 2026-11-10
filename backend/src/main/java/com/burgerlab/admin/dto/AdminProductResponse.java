package com.burgerlab.admin.dto;

public record AdminProductResponse(
        long id,
        long storeId,
        String code,
        String name,
        String categoryName,
        String price,
        boolean onSale,
        int version,
        int available,
        int reserved,
        int stockVersion
) {
}
