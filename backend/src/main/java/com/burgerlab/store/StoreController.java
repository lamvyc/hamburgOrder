package com.burgerlab.store;

import com.burgerlab.store.dto.StoreListResponse;
import com.burgerlab.store.dto.StoreResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public StoreListResponse listStores() {
        return storeService.listStores();
    }

    @GetMapping("/{storeId}")
    public StoreResponse getStore(@PathVariable("storeId") long storeId) {
        return storeService.getStore(storeId);
    }
}
