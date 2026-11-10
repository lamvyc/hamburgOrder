package com.burgerlab.order;

import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.order.dto.CreateOrderRequest;
import com.burgerlab.order.dto.OrderEventResponse;
import com.burgerlab.order.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderCancellationService orderCancellationService;

    public OrderController(
            OrderService orderService,
            OrderCancellationService orderCancellationService
    ) {
        this.orderService = orderService;
        this.orderCancellationService = orderCancellationService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        CreateOrderResult result = orderService.create(request, idempotencyKey, principal);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header("Idempotent-Replayed", Boolean.toString(result.replayed()))
                .header(HttpHeaders.LOCATION, "/api/orders/" + result.order().id())
                .body(result.order());
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderResponse> pay(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(orderService.payOrder(orderId, principal));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(orderCancellationService.cancelByCustomer(orderId, principal));
    }

    @GetMapping("/{orderId}/events")
    public ResponseEntity<java.util.List<OrderEventResponse>> events(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(orderService.getEvents(orderId, principal));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable("orderId") long orderId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(orderService.getOrder(orderId, principal));
    }
}
