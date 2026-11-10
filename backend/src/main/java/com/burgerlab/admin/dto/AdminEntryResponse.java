package com.burgerlab.admin.dto;

import java.time.OffsetDateTime;

public record AdminEntryResponse(
        String role,
        boolean available,
        String message,
        OffsetDateTime checkedAt
) {
}
