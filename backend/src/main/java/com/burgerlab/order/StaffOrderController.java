package com.burgerlab.order;

import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.order.dto.OrderResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/orders")
public class StaffOrderController {

    private final StaffOrderService staffOrderService;

    public StaffOrderController(StaffOrderService staffOrderService) {
        this.staffOrderService = staffOrderService;
    }

    @GetMapping
    public StaffOrderListResponse list(
            @RequestParam("storeId") long storeId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return staffOrderService.listActive(storeId, principal);
    }

    @PostMapping("/{orderId}/start")
    public OrderResponse start(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return staffOrderService.startMaking(orderId, principal);
    }

    @PostMapping("/{orderId}/ready")
    public OrderResponse ready(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return staffOrderService.markReady(orderId, principal);
    }

    @PostMapping("/{orderId}/complete")
    public OrderResponse complete(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return staffOrderService.complete(orderId, principal);
    }
}
