package com.burgerlab.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class MenuCacheStatsController {

    private final MenuCacheStats stats;

    public MenuCacheStatsController(MenuCacheStats stats) {
        this.stats = stats;
    }

    @GetMapping("/menu-cache")
    public MenuCacheStats.Snapshot menuCache() {
        return stats.snapshot();
    }
}
