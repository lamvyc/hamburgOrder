package com.burgerlab.admin;

import com.burgerlab.admin.dto.AdminStockBatchRequest;
import com.burgerlab.admin.dto.AdminStockBatchResponse;
import com.burgerlab.admin.dto.AdminStockPreviewResponse;
import com.burgerlab.auth.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stores")
public class AdminStockBatchController {

    private final AdminStockBatchService service;

    public AdminStockBatchController(AdminStockBatchService service) {
        this.service = service;
    }

    @PostMapping("/{storeId}/stock-adjustments/batch/preview")
    public AdminStockPreviewResponse preview(
            @PathVariable("storeId") long storeId,
            @Valid @RequestBody AdminStockBatchRequest request
    ) {
        return service.preview(storeId, request);
    }

    @PostMapping("/{storeId}/stock-adjustments/batch")
    public ResponseEntity<AdminStockBatchResponse> batch(
            @PathVariable("storeId") long storeId,
            @Valid @RequestBody AdminStockBatchRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        AdminStockBatchResult result = service.create(
                storeId, request, idempotencyKey, actor);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", Boolean.toString(result.replayed()))
                .body(result.response());
    }
}
