package com.burgerlab.platform.health.dto;

import java.time.OffsetDateTime;

/**
 * Process-level evidence only. This response must not query the database.
 */
public record LivenessResponse(
        String status,
        String service,
        String version,
        OffsetDateTime checkedAt,
        long uptimeSeconds
) {
}
