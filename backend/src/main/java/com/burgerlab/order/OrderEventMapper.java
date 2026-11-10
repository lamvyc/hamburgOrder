package com.burgerlab.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderEventMapper extends BaseMapper<OrderEventEntity> {

    @Select("""
            SELECT e.id,
                   e.event_type,
                   e.from_status,
                   e.to_status,
                   u.display_name AS operator_name,
                   e.reason,
                   e.created_at
              FROM order_events e
              LEFT JOIN users u ON u.id = e.operator_user_id
             WHERE e.order_id = #{orderId}
             ORDER BY e.id
            """)
    List<OrderEventRow> selectForOrder(@Param("orderId") long orderId);
}
