package com.burgerlab.admin;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminAuditLogMapper extends BaseMapper<AdminAuditLogEntity> {

    @Select("""
            SELECT a.id, a.action, a.target_type, a.target_id, a.detail,
                   a.created_at, u.display_name AS actor_name
              FROM admin_audit_logs a
              JOIN users u ON u.id = a.actor_user_id
             ORDER BY a.id DESC
             LIMIT #{limit}
            """)
    List<AdminAuditRow> selectRecent(@Param("limit") int limit);
}
