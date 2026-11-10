package com.burgerlab.catalog.dto;

/**
 * Price is intentionally a decimal string in JSON. The server uses BigDecimal
 * and DECIMAL; converting money to a JavaScript number at the boundary would
 * introduce avoidable rounding risk.
 */
public record MenuItemResponse(
        long id,
        String code,
        String name,
        String description,
        String price
) {
}
