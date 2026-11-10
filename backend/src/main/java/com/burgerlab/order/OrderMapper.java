package com.burgerlab.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    @Update("""
            UPDATE orders
               SET status = 'WAIT_MAKE',
                   version = version + 1,
                   updated_at = #{now}
             WHERE id = #{orderId}
               AND user_id = #{userId}
               AND status = 'WAIT_PAY'
               AND expires_at > #{now}
            """)
    int markPaid(
            @Param("orderId") long orderId,
            @Param("userId") long userId,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE orders
               SET status = #{toStatus},
                   version = version + 1,
                   updated_at = #{now}
             WHERE id = #{orderId}
               AND store_id = #{storeId}
               AND status = #{fromStatus}
            """)
    int transitionForStore(
            @Param("orderId") long orderId,
            @Param("storeId") long storeId,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE orders
               SET status = 'CANCELLED',
                   version = version + 1,
                   updated_at = #{now}
             WHERE id = #{orderId}
               AND user_id = #{userId}
               AND status = 'WAIT_PAY'
            """)
    int cancelByCustomer(
            @Param("orderId") long orderId,
            @Param("userId") long userId,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE orders
               SET status = 'CANCELLED',
                   version = version + 1,
                   updated_at = #{now}
             WHERE id = #{orderId}
               AND status = 'WAIT_PAY'
               AND expires_at <= #{cutoff}
            """)
    int cancelExpired(
            @Param("orderId") long orderId,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now
    );

    @Select("""
            SELECT id
              FROM orders
             WHERE status = 'WAIT_PAY'
               AND expires_at <= #{cutoff}
             ORDER BY expires_at, id
             LIMIT #{limit}
            """)
    List<Long> selectExpiredIds(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );
}
