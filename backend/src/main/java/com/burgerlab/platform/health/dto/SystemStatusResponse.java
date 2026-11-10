package com.burgerlab.platform.health.dto;

import com.burgerlab.platform.health.HealthState;
import java.time.OffsetDateTime;

public record SystemStatusResponse(
        HealthState status,
        OffsetDateTime checkedAt,
        String traceId,
        ApplicationStatus application,
        DatabaseStatus database
) {

    public record ApplicationStatus(
            HealthState status,
            String name,
            String version,
            String javaVersion,
            long uptimeSeconds
    ) {
    }

    public record DatabaseStatus(
            HealthState status,
            boolean connected,
            String product,
            String version,
            long latencyMs,
            boolean schemaReady,
            String flywayVersion,
            String lastMigration,
            String code,
            String message
    ) {
    }
}
