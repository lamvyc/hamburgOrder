package com.burgerlab.coupon;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplateEntity> {

    @Update("""
            UPDATE coupon_templates
               SET claimed_quantity = claimed_quantity + 1,
                   version = version + 1
             WHERE id = #{id}
               AND status = 'ACTIVE'
               AND claimed_quantity < total_quantity
               AND valid_from <= #{now}
               AND valid_to >= #{now}
            """)
    int claimOne(@Param("id") long id, @Param("now") LocalDateTime now);
}
