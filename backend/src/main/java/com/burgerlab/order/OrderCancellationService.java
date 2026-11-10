package com.burgerlab.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.common.error.BusinessConflictException;
import com.burgerlab.common.error.ResourceNotFoundException;
import com.burgerlab.coupon.CouponService;
import com.burgerlab.inventory.InventoryService;
import com.burgerlab.order.dto.OrderResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCancellationService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderEventMapper orderEventMapper;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final OrderService orderService;

    public OrderCancellationService(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            OrderEventMapper orderEventMapper,
            InventoryService inventoryService,
            CouponService couponService,
            OrderService orderService
    ) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderEventMapper = orderEventMapper;
        this.inventoryService = inventoryService;
        this.couponService = couponService;
        this.orderService = orderService;
    }

    @Transactional
    public OrderResponse cancelByCustomer(long orderId, AppUserPrincipal principal) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(principal.id())) {
            throw new ResourceNotFoundException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!OrderStatus.WAIT_PAY.name().equals(order.getStatus())) {
            throw new BusinessConflictException(
                    "ORDER_STATUS_CONFLICT", "订单当前状态不可取消：" + order.getStatus());
        }

        int affected = orderMapper.cancelByCustomer(orderId, principal.id(), now);
        if (affected != 1) {
            throw new BusinessConflictException(
                    "ORDER_STATUS_CONFLICT", "订单状态已变化，无法取消");
        }

        releaseItems(order, "CUSTOMER_CANCELLED", now);
        couponService.release(order.getCouponId(), orderId);
        insertCancelledEvent(orderId, principal.id(), "顾客取消订单", now);
        return orderService.toResponse(orderMapper.selectById(orderId));
    }

    /**
     * Each expired order gets its own transaction. One broken order must not
     * roll back the rest of the scan batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancelExpired(long orderId, LocalDateTime cutoff) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int affected = orderMapper.cancelExpired(orderId, cutoff, now);
        if (affected != 1) {
            // Payment or a manual cancel won the race, or another scanner did.
            return false;
        }

        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalStateException("Order disappeared after timeout cancellation");
        }
        releaseItems(order, "PAYMENT_TIMEOUT", now);
        couponService.release(order.getCouponId(), orderId);
        insertCancelledEvent(orderId, null, "支付超时自动取消", now);
        return true;
    }

    private void releaseItems(OrderEntity order, String reason, LocalDateTime now) {
        List<OrderItemEntity> items = orderItemMapper.selectList(
                Wrappers.<OrderItemEntity>lambdaQuery()
                        .eq(OrderItemEntity::getOrderId, order.getId())
                        .orderByAsc(OrderItemEntity::getProductId));
        for (OrderItemEntity item : items) {
            inventoryService.releaseReservation(
                    order.getId(),
                    order.getStoreId(),
                    item.getProductId(),
                    item.getQuantity(),
                    reason,
                    now);
        }
    }

    private void insertCancelledEvent(
            long orderId,
            Long operatorUserId,
            String reason,
            LocalDateTime now
    ) {
        OrderEventEntity event = new OrderEventEntity();
        event.setOrderId(orderId);
        event.setEventType("CANCELLED");
        event.setFromStatus(OrderStatus.WAIT_PAY.name());
        event.setToStatus(OrderStatus.CANCELLED.name());
        event.setOperatorUserId(operatorUserId);
        event.setReason(reason);
        event.setCreatedAt(now);
        orderEventMapper.insert(event);
    }
}
