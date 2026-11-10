package com.burgerlab.admin;

import com.burgerlab.admin.dto.AdminAuditLogResponse;
import com.burgerlab.admin.dto.AdminProductPageResponse;
import com.burgerlab.admin.dto.AdminProductResponse;
import com.burgerlab.admin.dto.AdminProductUpdateRequest;
import com.burgerlab.admin.dto.AdminStockAdjustmentRequest;
import com.burgerlab.admin.dto.AdminStockResponse;
import com.burgerlab.auth.AppUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final AdminInventoryService service;

    public AdminCatalogController(AdminInventoryService service) {
        this.service = service;
    }

    @GetMapping("/stores/{storeId}/products")
    public List<AdminProductResponse> products(@PathVariable("storeId") long storeId) {
        return service.listProducts(storeId);
    }

    @GetMapping("/stores/{storeId}/products/page")
    public AdminProductPageResponse productPage(
            @PathVariable("storeId") long storeId,
            @RequestParam(value = "afterId", required = false) Long afterId,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "onSale", required = false) Boolean onSale
    ) {
        return service.page(storeId, afterId, limit, onSale);
    }

    @PatchMapping("/products/{productId}")
    public AdminProductResponse updateProduct(
            @PathVariable("productId") long productId,
            @Valid @RequestBody AdminProductUpdateRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        return service.updateProduct(productId, request, actor);
    }

    @PostMapping("/products/{productId}/stock-adjustments")
    public AdminStockResponse adjustStock(
            @PathVariable("productId") long productId,
            @Valid @RequestBody AdminStockAdjustmentRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor
    ) {
        return service.adjustStock(productId, request, actor);
    }

    @GetMapping("/audit-logs")
    public List<AdminAuditLogResponse> auditLogs(
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return service.recentAudit(Math.max(1, Math.min(100, limit)));
    }
}
