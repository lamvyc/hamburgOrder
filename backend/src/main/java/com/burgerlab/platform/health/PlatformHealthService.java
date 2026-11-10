package com.burgerlab.platform.health;

import com.burgerlab.common.trace.TraceIdFilter;
import com.burgerlab.platform.health.dto.LivenessResponse;
import com.burgerlab.platform.health.dto.SystemStatusResponse;
import com.burgerlab.platform.health.dto.SystemStatusResponse.ApplicationStatus;
import com.burgerlab.platform.health.dto.SystemStatusResponse.DatabaseStatus;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Read-only operational checks. The liveness path intentionally never touches
 * the database; the status path proves both connectivity and that Flyway's
 * recorded migration can be read through the application's own SQL layer.
 */
@Service
public class PlatformHealthService {

    private static final Logger log = LoggerFactory.getLogger(PlatformHealthService.class);
    private static final RuntimeMXBean RUNTIME = ManagementFactory.getRuntimeMXBean();

    private final DataSource dataSource;
    private final SystemStatusMapper systemStatusMapper;
    private final String applicationName;
    private final String applicationVersion;

    public PlatformHealthService(
            DataSource dataSource,
            SystemStatusMapper systemStatusMapper,
            ObjectProvider<BuildProperties> buildProperties,
            @Value("${spring.application.name:burger-order-api}") String applicationName
    ) {
        this.dataSource = dataSource;
        this.systemStatusMapper = systemStatusMapper;
        this.applicationName = applicationName;
        this.applicationVersion = buildProperties.getIfAvailable() == null
                ? "dev"
                : buildProperties.getObject().getVersion();
    }

    public LivenessResponse liveness() {
        return new LivenessResponse(
                HealthState.UP.name(),
                applicationName,
                applicationVersion,
                OffsetDateTime.now(ZoneOffset.UTC),
                uptimeSeconds()
        );
    }

    public SystemStatusResponse status() {
        long startedAt = System.nanoTime();
        OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String traceId = TraceIdFilter.currentTraceId();

        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                return databaseDown(startedAt, checkedAt, traceId, "数据库连接校验失败");
            }

            DatabaseMetaData metadata = connection.getMetaData();
            String product = metadata.getDatabaseProductName();
            String version = metadata.getDatabaseProductVersion();

            try {
                SystemProbeRow probe = systemStatusMapper.selectProbe();
                boolean schemaReady = probe != null
                        && "1".equals(probe.getSchemaReady())
                        && probe.getFlywayVersion() != null;

                DatabaseStatus database = new DatabaseStatus(
                        schemaReady ? HealthState.UP : HealthState.DEGRADED,
                        true,
                        product,
                        version,
                        elapsedMs(startedAt),
                        schemaReady,
                        probe == null ? null : probe.getFlywayVersion(),
                        probe == null ? null : probe.getFlywayDescription(),
                        schemaReady ? null : "SCHEMA_NOT_READY",
                        schemaReady ? null : "数据库可连接，但迁移标记或迁移历史尚未就绪"
                );
                return response(database, checkedAt, traceId);
            } catch (DataAccessException exception) {
                log.warn("database_schema_probe_failed traceId={} error={}",
                        traceId, exception.getClass().getSimpleName());
                DatabaseStatus database = new DatabaseStatus(
                        HealthState.DEGRADED,
                        true,
                        product,
                        version,
                        elapsedMs(startedAt),
                        false,
                        null,
                        null,
                        "SCHEMA_NOT_READY",
                        "数据库可连接，但迁移状态读取失败"
                );
                return response(database, checkedAt, traceId);
            }
        } catch (SQLException exception) {
            log.warn("database_connectivity_probe_failed traceId={} error={}",
                    traceId, exception.getClass().getSimpleName());
            return databaseDown(startedAt, checkedAt, traceId, "无法连接数据库");
        }
    }

    private SystemStatusResponse response(
            DatabaseStatus database,
            OffsetDateTime checkedAt,
            String traceId
    ) {
        HealthState overall = database.status() == HealthState.UP
                ? HealthState.UP
                : database.status();

        ApplicationStatus application = new ApplicationStatus(
                HealthState.UP,
                applicationName,
                applicationVersion,
                Runtime.version().toString(),
                uptimeSeconds()
        );
        return new SystemStatusResponse(overall, checkedAt, traceId, application, database);
    }

    private SystemStatusResponse databaseDown(
            long startedAt,
            OffsetDateTime checkedAt,
            String traceId,
            String message
    ) {
        DatabaseStatus database = new DatabaseStatus(
                HealthState.DOWN,
                false,
                null,
                null,
                elapsedMs(startedAt),
                false,
                null,
                null,
                "DATABASE_UNAVAILABLE",
                message
        );
        return response(database, checkedAt, traceId);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private long uptimeSeconds() {
        return RUNTIME.getUptime() / 1000;
    }
}
