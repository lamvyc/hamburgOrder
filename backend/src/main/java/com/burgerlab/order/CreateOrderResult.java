package com.burgerlab.order;

import com.burgerlab.order.dto.OrderResponse;

public record CreateOrderResult(OrderResponse order, boolean replayed) {
}
