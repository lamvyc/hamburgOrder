package com.burgerlab.admin;

import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.catalog.MenuCacheService;
import com.burgerlab.common.error.BadRequestException;
import com.burgerlab.common.error.BusinessConflictException;
import com.burgerlab.common.error.ResourceNotFoundException;
import com.burgerlab.admin.dto.AdminAuditLogResponse;
import com.burgerlab.admin.dto.AdminProductPageResponse;
import com.burgerlab.admin.dto.AdminProductResponse;
import com.burgerlab.admin.dto.AdminProductUpdateRequest;
import com.burgerlab.admin.dto.AdminStockAdjustmentRequest;
import com.burgerlab.admin.dto.AdminStockResponse;
import com.burgerlab.inventory.StockLogEntity;
import com.burgerlab.inventory.StockLogMapper;
import com.burgerlab.store.StoreMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInventoryService {

    private final AdminCatalogMapper catalogMapper;
    private final AdminAuditLogMapper auditMapper;
    private final StockLogMapper stockLogMapper;
    private final StoreMapper storeMapper;
    private final ObjectMapper objectMapper;
    private final MenuCacheService menuCacheService;

    public AdminInventoryService(
            AdminCatalogMapper catalogMapper,
            AdminAuditLogMapper auditMapper,
            StockLogMapper stockLogMapper,
            StoreMapper storeMapper,
            ObjectMapper objectMapper,
            MenuCacheService menuCacheService
    ) {
        this.catalogMapper = catalogMapper;
        this.auditMapper = auditMapper;
        this.stockLogMapper = stockLogMapper;
        this.storeMapper = storeMapper;
        this.objectMapper = objectMapper;
        this.menuCacheService = menuCacheService;
    }

    @Transactional(readOnly = true)
    public List<AdminProductResponse> listProducts(long storeId) {
        if (storeMapper.selectById(storeId) == null) {
            throw new ResourceNotFoundException("STORE_NOT_FOUND", "门店不存在");
        }
        return catalogMapper.selectProducts(storeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminProductPageResponse page(
            long storeId,
            Long afterId,
            int requestedLimit,
            Boolean onSale
    ) {
        if (storeMapper.selectById(storeId) == null) {
            throw new ResourceNotFoundException("STORE_NOT_FOUND", "门店不存在");
        }
        int limit = Math.max(1, Math.min(50, requestedLimit));
        List<AdminProductRow> rows = catalogMapper.selectProductPage(
                storeId, afterId, onSale, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<AdminProductRow> pageRows = hasMore ? rows.subList(0, limit) : rows;
        List<AdminProductResponse> items = pageRows.stream().map(this::toResponse).toList();
        Long nextCursor = hasMore && !pageRows.isEmpty()
                ? pageRows.get(pageRows.size() - 1).getId()
                : null;
        return new AdminProductPageResponse(items, nextCursor, hasMore);
    }

    @Transactional
    public AdminProductResponse updateProduct(
            long productId,
            AdminProductUpdateRequest request,
            AppUserPrincipal actor
    ) {
        AdminProductRow before = catalogMapper.selectProduct(productId);
        if (before == null) {
            throw new ResourceNotFoundException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        int affected = catalogMapper.updateProduct(
                productId, request.price(), request.onSale(), request.version());
        if (affected != 1) {
            throw new BusinessConflictException(
                    "VERSION_CONFLICT", "商品已被其他管理员修改，请刷新后重试");
        }

        AdminProductRow after = catalogMapper.selectProduct(productId);
        audit(actor.id(), "PRODUCT_UPDATE", "PRODUCT", productId,
                Map.of(
                        "beforePrice", before.getPrice().toPlainString(),
                        "afterPrice", after.getPrice().toPlainString(),
                        "beforeOnSale", before.getOnSale(),
                        "afterOnSale", after.getOnSale()));
        menuCacheService.evictAfterCommit(before.getStoreId());
        return toResponse(after);
    }

    @Transactional
    public AdminStockResponse adjustStock(
            long productId,
            AdminStockAdjustmentRequest request,
            AppUserPrincipal actor
    ) {
        if (request.delta() == 0) {
            throw new BadRequestException("INVALID_STOCK_DELTA", "库存调整量不能为 0");
        }
        AdminProductRow before = catalogMapper.selectProduct(productId);
        if (before == null) {
            throw new ResourceNotFoundException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        int affected = catalogMapper.adjustStock(
                before.getStoreId(), productId, request.delta(), request.version());
        if (affected != 1) {
            AdminProductRow current = catalogMapper.selectProduct(productId);
            if (current == null) {
                throw new ResourceNotFoundException("PRODUCT_NOT_FOUND", "商品不存在");
            }
            if (current.getStockVersion() != request.version()) {
                throw new BusinessConflictException(
                        "VERSION_CONFLICT", "库存已被其他管理员修改，请刷新后重试");
            }
            if (current.getAvailable() + request.delta() < 0) {
                throw new BusinessConflictException(
                        "STOCK_INSUFFICIENT", "可售库存不足，不能调整为负数");
            }
            throw new BusinessConflictException(
                    "VERSION_CONFLICT", "库存状态已变化，请刷新后重试");
        }

        AdminProductRow after = catalogMapper.selectProduct(productId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        StockLogEntity log = new StockLogEntity();
        log.setOrderId(null);
        log.setOperatorUserId(actor.id());
        log.setStoreId(before.getStoreId());
        log.setProductId(productId);
        log.setChangeType("ADJUST");
        log.setAvailableDelta(request.delta());
        log.setReservedDelta(0);
        log.setAvailableAfter(after.getAvailable());
        log.setReservedAfter(after.getReserved());
        log.setReason(request.reason());
        log.setCreatedAt(now);
        stockLogMapper.insert(log);

        audit(actor.id(), "STOCK_ADJUST", "PRODUCT", productId,
                Map.of(
                        "beforeAvailable", before.getAvailable(),
                        "afterAvailable", after.getAvailable(),
                        "delta", request.delta(),
                        "reason", request.reason()));
        return new AdminStockResponse(
                productId, after.getAvailable(), after.getReserved(), after.getStockVersion());
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> recentAudit(int limit) {
        return auditMapper.selectRecent(limit).stream()
                .map(row -> new AdminAuditLogResponse(
                        row.getId(),
                        row.getActorName(),
                        row.getAction(),
                        row.getTargetType(),
                        row.getTargetId(),
                        row.getDetail(),
                        row.getCreatedAt().atOffset(ZoneOffset.UTC)))
                .toList();
    }

    private void audit(
            long actorId,
            String action,
            String targetType,
            long targetId,
            Map<String, Object> detail
    ) {
        AdminAuditLogEntity entity = new AdminAuditLogEntity();
        entity.setActorUserId(actorId);
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetail(json(detail));
        entity.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        auditMapper.insert(entity);
    }

    private String json(Map<String, Object> detail) {
        Map<String, Object> ordered = new LinkedHashMap<>(detail);
        try {
            return objectMapper.writeValueAsString(ordered);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize audit detail", exception);
        }
    }

    private AdminProductResponse toResponse(AdminProductRow row) {
        return new AdminProductResponse(
                row.getId(),
                row.getStoreId(),
                row.getCode(),
                row.getName(),
                row.getCategoryName(),
                money(row.getPrice()),
                Boolean.TRUE.equals(row.getOnSale()),
                row.getVersion(),
                row.getAvailable(),
                row.getReserved(),
                row.getStockVersion());
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }
}
