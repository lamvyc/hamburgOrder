package com.burgerlab.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.common.error.BusinessConflictException;
import com.burgerlab.common.error.ResourceNotFoundException;
import com.burgerlab.order.dto.OrderResponse;
import com.burgerlab.staff.StaffService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffOrderService {

    private final OrderMapper orderMapper;
    private final OrderEventMapper orderEventMapper;
    private final StaffService staffService;
    private final OrderService orderService;

    public StaffOrderService(
            OrderMapper orderMapper,
            OrderEventMapper orderEventMapper,
            StaffService staffService,
            OrderService orderService
    ) {
        this.orderMapper = orderMapper;
        this.orderEventMapper = orderEventMapper;
        this.staffService = staffService;
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public StaffOrderListResponse listActive(long storeId, AppUserPrincipal principal) {
        staffService.requireStoreAccess(principal.id(), storeId);
        List<OrderResponse> items = orderMapper.selectList(
                        Wrappers.<OrderEntity>lambdaQuery()
                                .eq(OrderEntity::getStoreId, storeId)
                                .in(OrderEntity::getStatus,
                                        OrderStatus.WAIT_MAKE.name(),
                                        OrderStatus.MAKING.name(),
                                        OrderStatus.READY.name())
                                .orderByAsc(OrderEntity::getCreatedAt)
                                .orderByAsc(OrderEntity::getId))
                .stream()
                .map(orderService::toResponse)
                .toList();
        return new StaffOrderListResponse(items);
    }

    @Transactional
    public OrderResponse startMaking(long orderId, AppUserPrincipal principal) {
        return transition(orderId, principal,
                OrderStatus.WAIT_MAKE, OrderStatus.MAKING,
                "MAKING_STARTED", "员工开始制作");
    }

    @Transactional
    public OrderResponse markReady(long orderId, AppUserPrincipal principal) {
        return transition(orderId, principal,
                OrderStatus.MAKING, OrderStatus.READY,
                "READY_FOR_PICKUP", "员工标记待取餐");
    }

    @Transactional
    public OrderResponse complete(long orderId, AppUserPrincipal principal) {
        return transition(orderId, principal,
                OrderStatus.READY, OrderStatus.COMPLETED,
                "COMPLETED", "员工完成订单");
    }

    private OrderResponse transition(
            long orderId,
            AppUserPrincipal principal,
            OrderStatus from,
            OrderStatus to,
            String eventType,
            String reason
    ) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null || !staffService.hasStoreAccess(principal.id(), order.getStoreId())) {
            throw new ResourceNotFoundException("ORDER_NOT_FOUND", "订单不存在");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int affected = orderMapper.transitionForStore(
                orderId, order.getStoreId(), from.name(), to.name(), now);
        if (affected != 1) {
            OrderEntity current = orderMapper.selectById(orderId);
            if (current == null) {
                throw new ResourceNotFoundException("ORDER_NOT_FOUND", "订单不存在");
            }
            throw new BusinessConflictException(
                    "ORDER_STATUS_CONFLICT",
                    "订单状态已变化，当前状态：" + current.getStatus());
        }

        OrderEventEntity event = new OrderEventEntity();
        event.setOrderId(orderId);
        event.setEventType(eventType);
        event.setFromStatus(from.name());
        event.setToStatus(to.name());
        event.setOperatorUserId(principal.id());
        event.setReason(reason);
        event.setCreatedAt(now);
        orderEventMapper.insert(event);

        return orderService.toResponse(orderMapper.selectById(orderId));
    }
}
