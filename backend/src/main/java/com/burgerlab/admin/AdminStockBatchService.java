package com.burgerlab.admin;

import com.burgerlab.admin.dto.AdminStockBatchLineRequest;
import com.burgerlab.admin.dto.AdminStockBatchLineResponse;
import com.burgerlab.admin.dto.AdminStockBatchRequest;
import com.burgerlab.admin.dto.AdminStockBatchResponse;
import com.burgerlab.admin.dto.AdminStockPreviewLineResponse;
import com.burgerlab.admin.dto.AdminStockPreviewResponse;
import com.burgerlab.auth.AppUserPrincipal;
import com.burgerlab.common.error.BadRequestException;
import com.burgerlab.common.error.BusinessConflictException;
import com.burgerlab.common.error.ResourceNotFoundException;
import com.burgerlab.inventory.StockLogEntity;
import com.burgerlab.inventory.StockLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStockBatchService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    private final AdminStockBatchMapper batchMapper;
    private final AdminStockBatchLineMapper lineMapper;
    private final AdminCatalogMapper catalogMapper;
    private final StockLogMapper stockLogMapper;
    private final AdminAuditLogMapper auditMapper;
    private final ObjectMapper objectMapper;

    public AdminStockBatchService(
            AdminStockBatchMapper batchMapper,
            AdminStockBatchLineMapper lineMapper,
            AdminCatalogMapper catalogMapper,
            StockLogMapper stockLogMapper,
            AdminAuditLogMapper auditMapper,
            ObjectMapper objectMapper
    ) {
        this.batchMapper = batchMapper;
        this.lineMapper = lineMapper;
        this.catalogMapper = catalogMapper;
        this.stockLogMapper = stockLogMapper;
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AdminStockBatchResult create(
            long storeId,
            AdminStockBatchRequest request,
            String idempotencyKey,
            AppUserPrincipal actor
    ) {
        String key = requireKey(idempotencyKey);
        List<AdminStockBatchLineRequest> lines = normalize(request);
        String hash = hash(storeId, lines);

        AdminStockBatchEntity existing = batchMapper.selectByKey(actor.id(), key);
        if (existing != null) {
            return replay(existing, hash);
        }

        AdminStockBatchEntity batch = new AdminStockBatchEntity();
        batch.setAdminUserId(actor.id());
        batch.setStoreId(storeId);
        batch.setIdempotencyKey(key);
        batch.setRequestHash(hash);
        batch.setStatus("PROCESSING");
        batch.setLineCount(lines.size());
        batch.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException duplicate) {
            // A concurrent request with the same key won the unique index.
            AdminStockBatchEntity concurrent = batchMapper.selectByKeyForUpdate(actor.id(), key);
            if (concurrent == null) {
                throw new BusinessConflictException(
                        "BATCH_IN_PROGRESS", "相同幂等键的批次正在处理中");
            }
            return replay(concurrent, hash);
        }

        List<AdminStockBatchLineResponse> applied = new ArrayList<>();
        for (AdminStockBatchLineRequest line : lines) {
            applied.add(applyLine(batch, storeId, line, actor));
        }

        batch.setStatus("COMPLETED");
        batchMapper.updateById(batch);
        audit(actor.id(), batch.getId(), storeId, applied);
        return new AdminStockBatchResult(
                new AdminStockBatchResponse(batch.getId(), applied.size(), applied), false);
    }

    @Transactional(readOnly = true)
    public AdminStockPreviewResponse preview(
            long storeId,
            AdminStockBatchRequest request
    ) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new BadRequestException("INVALID_BATCH", "批次至少包含一行");
        }
        if (request.lines().size() > 100) {
            throw new BadRequestException("INVALID_BATCH", "批次最多 100 行");
        }

        List<AdminStockPreviewLineResponse> results = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        int errors = 0;
        for (int i = 0; i < request.lines().size(); i++) {
            AdminStockBatchLineRequest line = request.lines().get(i);
            int lineNo = i + 1;
            String code = null;
            String message = null;
            Integer before = null;
            Integer after = null;

            if (!seen.add(line.productId())) {
                code = "DUPLICATE_BATCH_PRODUCT";
                message = "批次中重复商品";
            } else if (line.delta() == 0) {
                code = "INVALID_STOCK_DELTA";
                message = "调整量不能为 0";
            } else {
                AdminProductRow product = catalogMapper.selectProduct(line.productId());
                if (product == null || product.getStoreId() != storeId) {
                    code = "PRODUCT_NOT_FOUND";
                    message = "商品不存在或不属于该门店";
                } else {
                    before = product.getAvailable();
                    after = before + line.delta();
                    if (after < 0) {
                        code = "STOCK_INSUFFICIENT";
                        message = "可售库存不足";
                    }
                }
            }

            if (code != null) {
                errors++;
            }
            results.add(new AdminStockPreviewLineResponse(
                    lineNo,
                    line.productId(),
                    line.delta(),
                    line.reason(),
                    code == null,
                    code,
                    message,
                    before,
                    after));
        }
        return new AdminStockPreviewResponse(errors == 0, errors, results);
    }

    private AdminStockBatchLineResponse applyLine(
            AdminStockBatchEntity batch,
            long storeId,
            AdminStockBatchLineRequest line,
            AppUserPrincipal actor
    ) {
        AdminProductRow before = catalogMapper.selectProduct(line.productId());
        if (before == null || before.getStoreId() != storeId) {
            throw new ResourceNotFoundException("PRODUCT_NOT_FOUND", "商品不存在");
        }
        int affected = catalogMapper.adjustStockUnversioned(
                storeId, line.productId(), line.delta());
        if (affected != 1) {
            AdminProductRow current = catalogMapper.selectProduct(line.productId());
            if (current == null) {
                throw new ResourceNotFoundException("PRODUCT_NOT_FOUND", "商品不存在");
            }
            if (current.getAvailable() + line.delta() < 0) {
                throw new BusinessConflictException(
                        "STOCK_INSUFFICIENT", "商品可售库存不足：" + current.getCode());
            }
            throw new BusinessConflictException("STOCK_CONFLICT", "库存状态已变化");
        }

        AdminProductRow after = catalogMapper.selectProduct(line.productId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        StockLogEntity log = new StockLogEntity();
        log.setOrderId(null);
        log.setOperatorUserId(actor.id());
        log.setStoreId(storeId);
        log.setProductId(line.productId());
        log.setChangeType("ADJUST");
        log.setAvailableDelta(line.delta());
        log.setReservedDelta(0);
        log.setAvailableAfter(after.getAvailable());
        log.setReservedAfter(after.getReserved());
        log.setReason(("BATCH#" + batch.getId() + " " + line.reason())
                .substring(0, Math.min(255, ("BATCH#" + batch.getId() + " " + line.reason()).length())));
        log.setCreatedAt(now);
        stockLogMapper.insert(log);

        AdminStockBatchLineEntity entity = new AdminStockBatchLineEntity();
        entity.setBatchId(batch.getId());
        entity.setProductId(line.productId());
        entity.setDelta(line.delta());
        entity.setReason(line.reason());
        entity.setAvailableBefore(before.getAvailable());
        entity.setAvailableAfter(after.getAvailable());
        entity.setStockVersionAfter(after.getStockVersion());
        lineMapper.insert(entity);

        return new AdminStockBatchLineResponse(
                line.productId(),
                line.delta(),
                line.reason(),
                before.getAvailable(),
                after.getAvailable(),
                after.getStockVersion());
    }

    private AdminStockBatchResult replay(AdminStockBatchEntity batch, String hash) {
        if (!batch.getRequestHash().equals(hash)) {
            throw new BusinessConflictException(
                    "IDEMPOTENCY_CONFLICT", "相同幂等键的批次内容不一致");
        }
        if (!"COMPLETED".equals(batch.getStatus())) {
            throw new BusinessConflictException(
                    "BATCH_IN_PROGRESS", "相同幂等键的批次正在处理中");
        }
        List<AdminStockBatchLineResponse> lines = lineMapper.selectByBatch(batch.getId())
                .stream()
                .map(line -> new AdminStockBatchLineResponse(
                        line.getProductId(),
                        line.getDelta(),
                        line.getReason(),
                        line.getAvailableBefore(),
                        line.getAvailableAfter(),
                        line.getStockVersionAfter()))
                .toList();
        return new AdminStockBatchResult(
                new AdminStockBatchResponse(batch.getId(), lines.size(), lines), true);
    }

    private List<AdminStockBatchLineRequest> normalize(AdminStockBatchRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new BadRequestException("INVALID_BATCH", "批次至少包含一行");
        }
        if (request.lines().size() > 100) {
            throw new BadRequestException("INVALID_BATCH", "批次最多 100 行");
        }
        List<AdminStockBatchLineRequest> sorted = new ArrayList<>(request.lines());
        sorted.sort(Comparator.comparingLong(AdminStockBatchLineRequest::productId));
        Long previous = null;
        for (AdminStockBatchLineRequest line : sorted) {
            if (line.delta() == 0) {
                throw new BadRequestException("INVALID_STOCK_DELTA", "调整量不能为 0");
            }
            if (previous != null && previous.equals(line.productId())) {
                throw new BadRequestException("DUPLICATE_BATCH_PRODUCT", "批次中不能重复商品");
            }
            previous = line.productId();
        }
        return List.copyOf(sorted);
    }

    private String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED", "缺少 Idempotency-Key");
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new BadRequestException(
                    "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key 必须为 8–64 位安全字符");
        }
        return key;
    }

    private String hash(long storeId, List<AdminStockBatchLineRequest> lines) {
        String canonical = "storeId=" + storeId + "|lines=" + lines.stream()
                .map(line -> line.productId() + ":" + line.delta() + ":" + line.reason())
                .collect(java.util.stream.Collectors.joining(","));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void audit(
            long actorId,
            long batchId,
            long storeId,
            List<AdminStockBatchLineResponse> lines
    ) {
        AdminAuditLogEntity audit = new AdminAuditLogEntity();
        audit.setActorUserId(actorId);
        audit.setAction("STOCK_BATCH");
        audit.setTargetType("STORE");
        audit.setTargetId(storeId);
        audit.setDetail(json(Map.of(
                "batchId", batchId,
                "appliedCount", lines.size(),
                "products", lines.stream().map(AdminStockBatchLineResponse::productId).toList())));
        audit.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        auditMapper.insert(audit);
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize batch audit", exception);
        }
    }
}
