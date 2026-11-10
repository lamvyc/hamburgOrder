package com.burgerlab.order;

import com.burgerlab.order.dto.OrderResponse;
import java.util.List;

public record StaffOrderListResponse(List<OrderResponse> items) {

    public StaffOrderListResponse {
        items = List.copyOf(items);
    }
}
