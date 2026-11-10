package com.burgerlab.coupon;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.burgerlab.common.error.BusinessConflictException;
import com.burgerlab.common.error.ResourceNotFoundException;
import com.burgerlab.coupon.dto.CouponTemplateResponse;
import com.burgerlab.coupon.dto.UserCouponResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;

    public CouponService(
            CouponTemplateMapper templateMapper,
            UserCouponMapper userCouponMapper
    ) {
        this.templateMapper = templateMapper;
        this.userCouponMapper = userCouponMapper;
    }

    @Transactional(readOnly = true)
    public List<CouponTemplateResponse> available(long userId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return templateMapper.selectList(Wrappers.<CouponTemplateEntity>lambdaQuery()
                        .eq(CouponTemplateEntity::getStatus, "ACTIVE")
                        .le(CouponTemplateEntity::getValidFrom, now)
                        .ge(CouponTemplateEntity::getValidTo, now)
                        .orderByAsc(CouponTemplateEntity::getId))
                .stream()
                .map(template -> new CouponTemplateResponse(
                        template.getId(),
                        template.getCode(),
                        template.getName(),
                        money(template.getDiscountAmount()),
                        money(template.getMinOrderAmount()),
                        template.getTotalQuantity() - template.getClaimedQuantity(),
                        userCouponMapper.selectCount(Wrappers.<UserCouponEntity>lambdaQuery()
                                .eq(UserCouponEntity::getTemplateId, template.getId())
                                .eq(UserCouponEntity::getUserId, userId)) > 0,
                        atUtc(template.getValidFrom()),
                        atUtc(template.getValidTo())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserCouponResponse> mine(long userId) {
        return userCouponMapper.selectForUser(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserCouponResponse claim(long templateId, long userId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        CouponTemplateEntity template = templateMapper.selectById(templateId);
        if (template == null || !isActive(template, now)) {
            throw new BusinessConflictException(
                    "COUPON_NOT_AVAILABLE", "优惠券不存在或不在领取时间");
        }
        if (userCouponMapper.selectCount(Wrappers.<UserCouponEntity>lambdaQuery()
                .eq(UserCouponEntity::getTemplateId, templateId)
                .eq(UserCouponEntity::getUserId, userId)) > 0) {
            throw new BusinessConflictException(
                    "COUPON_ALREADY_CLAIMED", "该优惠券已领取");
        }

        // The conditional increment is the total-quantity gate. The insert's
        // unique key is the per-user gate; both live in this transaction.
        int affected = templateMapper.claimOne(templateId, now);
        if (affected != 1) {
            throw new BusinessConflictException("COUPON_SOLD_OUT", "优惠券已领完");
        }

        UserCouponEntity coupon = new UserCouponEntity();
        coupon.setTemplateId(templateId);
        coupon.setUserId(userId);
        coupon.setStatus("UNUSED");
        coupon.setClaimedAt(now);
        coupon.setVersion(0);
        try {
            userCouponMapper.insert(coupon);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessConflictException(
                    "COUPON_ALREADY_CLAIMED", "该优惠券已领取");
        }
        return toResponse(userCouponMapper.selectRow(coupon.getId()));
    }

    @Transactional(readOnly = true)
    public CouponApplication validateForOrder(
            Long userCouponId,
            long userId,
            BigDecimal subtotal,
            LocalDateTime now
    ) {
        if (userCouponId == null) {
            return null;
        }
        UserCouponRow row = userCouponMapper.selectRow(userCouponId);
        if (row == null || !ownerOf(userCouponId, userId)) {
            throw new ResourceNotFoundException("COUPON_NOT_FOUND", "优惠券不存在");
        }
        if (!"UNUSED".equals(row.getStatus())) {
            throw new BusinessConflictException(
                    "COUPON_NOT_USABLE", "优惠券已锁定或已使用");
        }
        CouponTemplateEntity template = templateMapper.selectById(row.getTemplateId());
        if (template == null || !isActive(template, now)) {
            throw new BusinessConflictException(
                    "COUPON_NOT_USABLE", "优惠券不在可用时间");
        }
        if (subtotal.compareTo(template.getMinOrderAmount()) < 0) {
            throw new BusinessConflictException(
                    "COUPON_MIN_ORDER_NOT_MET", "订单金额未达到优惠券门槛");
        }
        BigDecimal discount = template.getDiscountAmount().min(subtotal);
        return new CouponApplication(userCouponId, discount);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForOrder(long userCouponId, long userId, long orderId) {
        if (userCouponMapper.lockForOrder(userCouponId, userId, orderId) != 1) {
            throw new BusinessConflictException(
                    "COUPON_NOT_USABLE", "优惠券已被其他订单使用");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markUsed(Long userCouponId, long orderId, LocalDateTime now) {
        if (userCouponId == null) {
            return;
        }
        if (userCouponMapper.markUsed(userCouponId, orderId, now) != 1) {
            throw new BusinessConflictException(
                    "COUPON_STATE_INCONSISTENT", "优惠券锁定状态不一致");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long userCouponId, long orderId) {
        if (userCouponId == null) {
            return;
        }
        if (userCouponMapper.release(userCouponId, orderId) != 1) {
            throw new BusinessConflictException(
                    "COUPON_STATE_INCONSISTENT", "优惠券锁定状态不一致");
        }
    }

    private boolean ownerOf(long userCouponId, long userId) {
        UserCouponEntity entity = userCouponMapper.selectById(userCouponId);
        return entity != null && entity.getUserId() == userId;
    }

    private boolean isActive(CouponTemplateEntity template, LocalDateTime now) {
        return "ACTIVE".equals(template.getStatus())
                && !template.getValidFrom().isAfter(now)
                && !template.getValidTo().isBefore(now);
    }

    private UserCouponResponse toResponse(UserCouponRow row) {
        return new UserCouponResponse(
                row.getId(),
                row.getTemplateId(),
                row.getCode(),
                row.getName(),
                money(row.getDiscountAmount()),
                money(row.getMinOrderAmount()),
                row.getStatus(),
                row.getOrderId(),
                atUtc(row.getClaimedAt()),
                row.getUsedAt() == null ? null : atUtc(row.getUsedAt()));
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    private OffsetDateTime atUtc(LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
