package com.burgerlab.coupon;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCouponEntity> {

    @Update("""
            UPDATE user_coupons
               SET status = 'LOCKED', order_id = #{orderId}, version = version + 1
             WHERE id = #{id} AND user_id = #{userId} AND status = 'UNUSED'
            """)
    int lockForOrder(
            @Param("id") long id,
            @Param("userId") long userId,
            @Param("orderId") long orderId
    );

    @Update("""
            UPDATE user_coupons
               SET status = 'USED', used_at = #{now}, version = version + 1
             WHERE id = #{id} AND order_id = #{orderId} AND status = 'LOCKED'
            """)
    int markUsed(
            @Param("id") long id,
            @Param("orderId") long orderId,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE user_coupons
               SET status = 'UNUSED', order_id = NULL, used_at = NULL, version = version + 1
             WHERE id = #{id} AND order_id = #{orderId} AND status = 'LOCKED'
            """)
    int release(
            @Param("id") long id,
            @Param("orderId") long orderId
    );

    @Select("""
            SELECT uc.id, uc.template_id, ct.code, ct.name,
                   ct.discount_amount, ct.min_order_amount, uc.status,
                   uc.order_id, uc.claimed_at, uc.used_at
              FROM user_coupons uc
              JOIN coupon_templates ct ON ct.id = uc.template_id
             WHERE uc.user_id = #{userId}
             ORDER BY uc.id DESC
            """)
    List<UserCouponRow> selectForUser(@Param("userId") long userId);

    @Select("""
            SELECT uc.id, uc.template_id, ct.code, ct.name,
                   ct.discount_amount, ct.min_order_amount, uc.status,
                   uc.order_id, uc.claimed_at, uc.used_at
              FROM user_coupons uc
              JOIN coupon_templates ct ON ct.id = uc.template_id
             WHERE uc.id = #{id}
            """)
    UserCouponRow selectRow(@Param("id") long id);
}
