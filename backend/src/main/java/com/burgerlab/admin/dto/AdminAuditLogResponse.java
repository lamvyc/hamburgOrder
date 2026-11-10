package com.burgerlab.admin.dto;

import java.time.OffsetDateTime;

public record AdminAuditLogResponse(
        long id,
        String actorName,
        String action,
        String targetType,
        long targetId,
        String detail,
        OffsetDateTime createdAt
) {
}
