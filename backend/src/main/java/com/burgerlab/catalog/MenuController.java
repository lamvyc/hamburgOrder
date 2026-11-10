package com.burgerlab.catalog;

import com.burgerlab.catalog.dto.MenuResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores")
public class MenuController {

    private final MenuCacheService menuCacheService;

    public MenuController(MenuCacheService menuCacheService) {
        this.menuCacheService = menuCacheService;
    }

    @GetMapping("/{storeId}/menu")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable("storeId") long storeId) {
        MenuCacheService.CachedMenu cached = menuCacheService.getMenu(storeId);
        return ResponseEntity.ok()
                .header("X-Cache", cached.cacheStatus())
                .body(cached.menu());
    }
}
