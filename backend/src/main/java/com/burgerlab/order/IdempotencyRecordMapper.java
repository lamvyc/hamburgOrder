package com.burgerlab.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordEntity> {

    /**
     * The unique key insert is the first statement in the order transaction.
     * A concurrent duplicate waits for the first transaction, then this current
     * read sees whether it completed or failed.
     */
    @Select("""
            SELECT *
              FROM idempotency_records
             WHERE user_id = #{userId}
               AND operation = #{operation}
               AND idempotency_key = #{idempotencyKey}
             FOR UPDATE
            """)
    IdempotencyRecordEntity selectForUpdate(
            @Param("userId") long userId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey
    );
}
