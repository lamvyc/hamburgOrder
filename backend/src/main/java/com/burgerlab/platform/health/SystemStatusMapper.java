package com.burgerlab.platform.health;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * Stage 0 deliberately uses explicit SQL instead of hiding the probe behind a
 * framework abstraction. Later business queries will follow the same rule for
 * stock updates, state transitions, cursor pagination and statistics.
 */
@Mapper
public interface SystemStatusMapper {

    @Select("""
            SELECT
                (SELECT meta_value
                   FROM system_metadata
                  WHERE meta_key = 'schema_ready') AS schema_ready,
                (SELECT version
                   FROM flyway_schema_history
                  WHERE success = 1
                  ORDER BY installed_rank DESC
                  LIMIT 1) AS flyway_version,
                (SELECT description
                   FROM flyway_schema_history
                  WHERE success = 1
                  ORDER BY installed_rank DESC
                  LIMIT 1) AS flyway_description
            """)
    SystemProbeRow selectProbe();
}
