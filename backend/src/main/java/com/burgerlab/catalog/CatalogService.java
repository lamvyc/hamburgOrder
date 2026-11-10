package com.burgerlab.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.burgerlab.catalog.dto.MenuCategoryResponse;
import com.burgerlab.catalog.dto.MenuItemResponse;
import com.burgerlab.catalog.dto.MenuResponse;
import com.burgerlab.store.StoreService;
import com.burgerlab.store.dto.StoreResponse;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final StoreService storeService;
    private final ProductMapper productMapper;

    public CatalogService(StoreService storeService, ProductMapper productMapper) {
        this.storeService = storeService;
        this.productMapper = productMapper;
    }

    @Transactional(readOnly = true)
    public MenuResponse getMenu(long storeId) {
        // Reuses the store ownership/state policy instead of duplicating it in
        // the menu query. A missing store is a 404, not an empty menu.
        StoreResponse store = storeService.getStore(storeId);

        List<ProductEntity> products = productMapper.selectList(
                Wrappers.<ProductEntity>lambdaQuery()
                        .eq(ProductEntity::getStoreId, storeId)
                        .eq(ProductEntity::getOnSale, true)
                        .orderByAsc(ProductEntity::getCategorySort)
                        .orderByAsc(ProductEntity::getSortOrder)
                        .orderByAsc(ProductEntity::getId)
        );

        Map<String, String> categoryNames = new LinkedHashMap<>();
        Map<String, List<MenuItemResponse>> productsByCategory = new LinkedHashMap<>();
        for (ProductEntity product : products) {
            categoryNames.putIfAbsent(product.getCategoryCode(), product.getCategoryName());
            productsByCategory.computeIfAbsent(product.getCategoryCode(), ignored -> new ArrayList<>())
                    .add(toMenuItem(product));
        }

        List<MenuCategoryResponse> categories = productsByCategory.entrySet().stream()
                .map(entry -> new MenuCategoryResponse(
                        entry.getKey(),
                        categoryNames.get(entry.getKey()),
                        entry.getValue()))
                .toList();

        return new MenuResponse(store, categories);
    }

    private MenuItemResponse toMenuItem(ProductEntity product) {
        String price = product.getPrice()
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();
        return new MenuItemResponse(
                product.getId(),
                product.getProductCode(),
                product.getName(),
                product.getDescription(),
                price
        );
    }
}
