package com.burgerlab.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.catalog.ProductEntity;
import com.burgerlab.catalog.ProductMapper;
import com.burgerlab.common.error.BadRequestException;
import com.burgerlab.common.error.BusinessConflictException;
import com.burgerlab.common.error.ResourceNotFoundException;
import com.burgerlab.coupon.CouponApplication;
import com.burgerlab.coupon.CouponService;
import com.burgerlab.inventory.InventoryService;
import com.burgerlab.order.dto.CreateOrderItemRequest;
import com.burgerlab.order.dto.CreateOrderRequest;
import com.burgerlab.order.dto.OrderEventResponse;
import com.burgerlab.order.dto.OrderItemResponse;
import com.burgerlab.order.dto.OrderResponse;
import com.burgerlab.store.StoreEntity;
import com.burgerlab.store.StoreMapper;
import com.burgerlab.store.StoreService;
import com.burgerlab.store.dto.StoreResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final String OPERATION_CREATE_ORDER = "CREATE_ORDER";
    private static final int EXPIRE_MINUTES = 30;

    private final StoreService storeService;
    private final StoreMapper storeMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderEventMapper orderEventMapper;
    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final InventoryService inventoryService;
    private final CouponService couponService;

    public OrderService(
            StoreService storeService,
            StoreMapper storeMapper,
            ProductMapper productMapper,
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            OrderEventMapper orderEventMapper,
            IdempotencyRecordMapper idempotencyRecordMapper,
            InventoryService inventoryService,
            CouponService couponService
    ) {
        this.storeService = storeService;
        this.storeMapper = storeMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderEventMapper = orderEventMapper;
        this.idempotencyRecordMapper = idempotencyRecordMapper;
        this.inventoryService = inventoryService;
        this.couponService = couponService;
    }

    @Transactional
    public CreateOrderResult create(
            CreateOrderRequest request,
            String idempotencyKey,
            AppUserPrincipal principal
    ) {
        String key = requireIdempotencyKey(idempotencyKey);
        long storeId = requireStoreId(request);
        List<RequestedItem> requestedItems = normalizeAndSortItems(request.items());
        String requestHash = requestHash(storeId, requestedItems, request.userCouponId());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        IdempotencyRecordEntity idempotency = new IdempotencyRecordEntity();
        idempotency.setUserId(principal.id());
        idempotency.setOperation(OPERATION_CREATE_ORDER);
        idempotency.setIdempotencyKey(key);
        idempotency.setRequestHash(requestHash);
        idempotency.setStatus("PROCESSING");
        idempotency.setCreatedAt(now);

        try {
            // This statement is the concurrency gate for the same key. The
            // unique constraint is the database-level guarantee.
            idempotencyRecordMapper.insert(idempotency);
        } catch (DuplicateKeyException duplicate) {
            return replay(principal, key, requestHash);
        }

        StoreResponse store = storeService.getStore(storeId);
        if (!store.orderable()) {
            throw new BusinessConflictException("STORE_NOT_ORDERABLE", "门店当前不接单");
        }

        Map<Long, ProductEntity> products = loadProducts(storeId, requestedItems);
        List<OrderItemEntity> orderItems = buildOrderItems(
                requestedItems, products, now);
        BigDecimal subtotal = orderItems.stream()
                .map(OrderItemEntity::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
        CouponApplication coupon = couponService.validateForOrder(
                request.userCouponId(), principal.id(), subtotal, now);
        BigDecimal discount = coupon == null ? BigDecimal.ZERO : coupon.discountAmount();
        BigDecimal totalAmount = subtotal.subtract(discount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.UNNECESSARY);

        OrderEntity order = new OrderEntity();
        order.setOrderNo(newOrderNo(now));
        order.setUserId(principal.id());
        order.setStoreId(storeId);
        order.setCouponId(coupon == null ? null : coupon.userCouponId());
        order.setStatus(OrderStatus.WAIT_PAY.name());
        order.setCurrency("CNY");
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discount);
        order.setExpiresAt(now.plusMinutes(EXPIRE_MINUTES));
        order.setVersion(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        if (coupon != null) {
            couponService.lockForOrder(coupon.userCouponId(), principal.id(), order.getId());
        }

        for (OrderItemEntity item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // Fixed product-id order limits lock-order inversions between two
        // multi-item orders in the same store.
        for (RequestedItem requested : requestedItems) {
            ProductEntity product = products.get(requested.productId());
            inventoryService.reserve(
                    order.getId(),
                    storeId,
                    requested.productId(),
                    product.getName(),
                    requested.quantity(),
                    now
            );
        }

        OrderEventEntity event = new OrderEventEntity();
        event.setOrderId(order.getId());
        event.setEventType("CREATED");
        event.setFromStatus(null);
        event.setToStatus(OrderStatus.WAIT_PAY.name());
        event.setOperatorUserId(principal.id());
        event.setReason("顾客创建订单");
        event.setCreatedAt(now);
        orderEventMapper.insert(event);

        idempotency.setStatus("COMPLETED");
        idempotency.setOrderId(order.getId());
        idempotency.setCompletedAt(now);
        idempotencyRecordMapper.updateById(idempotency);

        return new CreateOrderResult(toResponse(order), false);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(long orderId, AppUserPrincipal principal) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(principal.id())) {
            throw new ResourceNotFoundException("ORDER_NOT_FOUND", "订单不存在");
        }
        return toResponse(order);
    }

    @Transactional
    public OrderResponse payOrder(long orderId, AppUserPrincipal principal) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(principal.id())) {
            throw new ResourceNotFoundException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!OrderStatus.WAIT_PAY.name().equals(order.getStatus())) {
            throw new BusinessConflictException(
                    "ORDER_STATUS_CONFLICT", "订单当前状态不可支付：" + order.getStatus());
        }
        if (!order.getExpiresAt().isAfter(now)) {
            throw new BusinessConflictException("ORDER_EXPIRED", "订单已过期，无法支付");
        }

        int updated = orderMapper.markPaid(orderId, principal.id(), now);
        if (updated != 1) {
            OrderEntity current = orderMapper.selectById(orderId);
            if (current == null) {
                throw new ResourceNotFoundException("ORDER_NOT_FOUND", "订单不存在");
            }
            if (!current.getExpiresAt().isAfter(now)) {
                throw new BusinessConflictException("ORDER_EXPIRED", "订单已过期，无法支付");
            }
            throw new BusinessConflictException(
                    "ORDER_STATUS_CONFLICT", "订单状态已变化，当前状态：" + current.getStatus());
        }

        List<OrderItemEntity> items = orderItemMapper.selectList(
                Wrappers.<OrderItemEntity>lambdaQuery()
                        .eq(OrderItemEntity::getOrderId, orderId)
                        .orderByAsc(OrderItemEntity::getProductId));
        for (OrderItemEntity item : items) {
            inventoryService.commitReservation(
                    orderId,
                    order.getStoreId(),
                    item.getProductId(),
                    item.getQuantity(),
                    now
            );
        }

        couponService.markUsed(order.getCouponId(), orderId, now);
        insertEvent(orderId, "PAYMENT_SUCCEEDED",
                OrderStatus.WAIT_PAY, OrderStatus.WAIT_MAKE,
                principal.id(), "模拟支付成功", now);

        return toResponse(orderMapper.selectById(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> getEvents(long orderId, AppUserPrincipal principal) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(principal.id())) {
            throw new ResourceNotFoundException("ORDER_NOT_FOUND", "订单不存在");
        }
        return orderEventMapper.selectForOrder(orderId).stream()
                .map(row -> new OrderEventResponse(
                        row.getId(),
                        row.getEventType(),
                        row.getFromStatus() == null ? null : OrderStatus.valueOf(row.getFromStatus()),
                        OrderStatus.valueOf(row.getToStatus()),
                        row.getOperatorName(),
                        row.getReason(),
                        atUtc(row.getCreatedAt())))
                .toList();
    }

    public OrderResponse toResponse(OrderEntity order) {
        return buildOrderResponse(order);
    }

    private void insertEvent(
            long orderId,
            String eventType,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            long operatorUserId,
            String reason,
            LocalDateTime now
    ) {
        OrderEventEntity event = new OrderEventEntity();
        event.setOrderId(orderId);
        event.setEventType(eventType);
        event.setFromStatus(fromStatus == null ? null : fromStatus.name());
        event.setToStatus(toStatus.name());
        event.setOperatorUserId(operatorUserId);
        event.setReason(reason);
        event.setCreatedAt(now);
        orderEventMapper.insert(event);
    }

    private CreateOrderResult replay(
            AppUserPrincipal principal,
            String key,
            String requestHash
    ) {
        IdempotencyRecordEntity existing = idempotencyRecordMapper.selectForUpdate(
                principal.id(), OPERATION_CREATE_ORDER, key);
        if (existing == null) {
            throw new IllegalStateException("Idempotency row disappeared after duplicate");
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessConflictException(
                    "IDEMPOTENCY_CONFLICT", "相同幂等键的请求内容不一致");
        }
        if (!"COMPLETED".equals(existing.getStatus()) || existing.getOrderId() == null) {
            throw new BusinessConflictException(
                    "IDEMPOTENCY_IN_PROGRESS", "相同幂等键的请求正在处理中");
        }

        OrderEntity order = orderMapper.selectById(existing.getOrderId());
        if (order == null || !order.getUserId().equals(principal.id())) {
            throw new IllegalStateException("Idempotency order is missing or owned by another user");
        }
        return new CreateOrderResult(toResponse(order), true);
    }

    private OrderResponse buildOrderResponse(OrderEntity order) {
        StoreEntity store = storeMapper.selectById(order.getStoreId());
        if (store == null) {
            throw new IllegalStateException("Order store is missing");
        }
        List<OrderItemResponse> items = orderItemMapper.selectList(
                        Wrappers.<OrderItemEntity>lambdaQuery()
                                .eq(OrderItemEntity::getOrderId, order.getId())
                                .orderByAsc(OrderItemEntity::getId))
                .stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getProductCode(),
                        item.getProductName(),
                        money(item.getUnitPrice()),
                        item.getQuantity(),
                        money(item.getLineAmount())))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                OrderStatus.valueOf(order.getStatus()),
                order.getStoreId(),
                store.getName(),
                money(order.getTotalAmount()),
                money(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount()),
                order.getCurrency(),
                atUtc(order.getExpiresAt()),
                atUtc(order.getCreatedAt()),
                items
        );
    }

    private Map<Long, ProductEntity> loadProducts(
            long storeId,
            List<RequestedItem> requestedItems
    ) {
        List<Long> ids = requestedItems.stream().map(RequestedItem::productId).toList();
        Map<Long, ProductEntity> products = productMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        for (RequestedItem requested : requestedItems) {
            ProductEntity product = products.get(requested.productId());
            if (product == null
                    || product.getStoreId() != storeId
                    || !Boolean.TRUE.equals(product.getOnSale())) {
                throw new BusinessConflictException(
                        "PRODUCT_UNAVAILABLE",
                        "商品不存在、已下架或不属于当前门店：" + requested.productId());
            }
        }
        return products;
    }

    private List<OrderItemEntity> buildOrderItems(
            List<RequestedItem> requestedItems,
            Map<Long, ProductEntity> products,
            LocalDateTime now
    ) {
        List<OrderItemEntity> items = new ArrayList<>();
        for (RequestedItem requested : requestedItems) {
            ProductEntity product = products.get(requested.productId());
            BigDecimal unitPrice = product.getPrice().setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal lineAmount = unitPrice
                    .multiply(BigDecimal.valueOf(requested.quantity()))
                    .setScale(2, RoundingMode.UNNECESSARY);

            OrderItemEntity item = new OrderItemEntity();
            item.setProductId(product.getId());
            item.setProductCode(product.getProductCode());
            item.setProductName(product.getName());
            item.setUnitPrice(unitPrice);
            item.setQuantity(requested.quantity());
            item.setLineAmount(lineAmount);
            item.setCreatedAt(now);
            items.add(item);
        }
        return items;
    }

    private String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED", "缺少 Idempotency-Key");
        }
        if (!key.matches("[A-Za-z0-9._-]{8,64}")) {
            throw new BadRequestException(
                    "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key 格式不正确");
        }
        return key;
    }

    private long requireStoreId(CreateOrderRequest request) {
        if (request == null || request.storeId() == null) {
            throw new BadRequestException("INVALID_ORDER", "门店 ID 不能为空");
        }
        return request.storeId();
    }

    private List<RequestedItem> normalizeAndSortItems(List<CreateOrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("INVALID_ORDER_ITEMS", "订单至少包含一个商品");
        }
        if (items.size() > 20) {
            throw new BadRequestException("INVALID_ORDER_ITEMS", "单笔订单最多包含 20 种商品");
        }

        Map<Long, Integer> byProduct = new LinkedHashMap<>();
        for (CreateOrderItemRequest item : items) {
            if (item == null || item.productId() == null || item.quantity() == null) {
                throw new BadRequestException("INVALID_ORDER_ITEMS", "商品和数量不能为空");
            }
            if (item.quantity() < 1 || item.quantity() > 99) {
                throw new BadRequestException("INVALID_ORDER_ITEMS", "商品数量必须在 1–99 之间");
            }
            if (byProduct.putIfAbsent(item.productId(), item.quantity()) != null) {
                throw new BadRequestException("DUPLICATE_ORDER_ITEM", "订单中不能重复商品");
            }
        }

        return byProduct.entrySet().stream()
                .map(entry -> new RequestedItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(RequestedItem::productId))
                .toList();
    }

    private String requestHash(long storeId, List<RequestedItem> items, Long userCouponId) {
        String canonical = "storeId=" + storeId
                + "|userCouponId=" + (userCouponId == null ? "" : userCouponId)
                + "|items=" + items.stream()
                .map(item -> item.productId() + ":" + item.quantity())
                .collect(Collectors.joining(","));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String newOrderNo(LocalDateTime now) {
        return "BO"
                + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 6)
                        .toUpperCase(Locale.ROOT);
    }

    private OffsetDateTime atUtc(LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private record RequestedItem(long productId, int quantity) {
    }
}
