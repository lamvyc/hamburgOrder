package com.burgerlab.admin;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminStockBatchMapper extends BaseMapper<AdminStockBatchEntity> {

    @Select("""
            SELECT * FROM admin_stock_batches
             WHERE admin_user_id = #{adminUserId}
               AND idempotency_key = #{idempotencyKey}
            """)
    AdminStockBatchEntity selectByKey(
            @Param("adminUserId") long adminUserId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Select("""
            SELECT * FROM admin_stock_batches
             WHERE admin_user_id = #{adminUserId}
               AND idempotency_key = #{idempotencyKey}
             FOR UPDATE
            """)
    AdminStockBatchEntity selectByKeyForUpdate(
            @Param("adminUserId") long adminUserId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
