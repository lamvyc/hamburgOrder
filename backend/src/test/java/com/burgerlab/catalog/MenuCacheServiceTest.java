package com.burgerlab.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.burgerlab.catalog.dto.MenuResponse;
import com.burgerlab.store.StoreBusinessStatus;
import com.burgerlab.store.dto.StoreResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MenuCacheServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    @Mock
    private CatalogService catalogService;

    @Test
    void concurrentMissesUseOneDatabaseLoad() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        MenuResponse menu = new MenuResponse(new StoreResponse(
                1L, "CENTRAL", "中央车站店", "地址", new java.math.BigDecimal("31.0"),
                new java.math.BigDecimal("121.0"), StoreBusinessStatus.OPEN,
                true, true, "10:00–22:00"), List.of());
        AtomicInteger loads = new AtomicInteger();
        when(catalogService.getMenu(1L)).thenAnswer(invocation -> {
            loads.incrementAndGet();
            Thread.sleep(150);
            return menu;
        });
        MenuCacheStats stats = new MenuCacheStats(new SimpleMeterRegistry());
        MenuCacheService service = new MenuCacheService(
                redis, new ObjectMapper(), catalogService, stats,
                60, 0, 10, true);

        int workers = 4;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<MenuCacheService.CachedMenu>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.getMenu(1L);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<MenuCacheService.CachedMenu> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS).menu()).isSameAs(menu);
            }
        } finally {
            executor.shutdownNow();
        }

        verify(catalogService, times(1)).getMenu(1L);
        assertThat(loads.get()).isEqualTo(1);
        assertThat(stats.snapshot().stampedeWait()).isGreaterThan(0);
    }

    @Test
    void redisFailureFallsBackToDatabaseAndReportsBypass() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("menu:v1:1"))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        MenuResponse menu = new MenuResponse(new StoreResponse(
                1L, "CENTRAL", "中央车站店", "地址", new java.math.BigDecimal("31.0"), new java.math.BigDecimal("121.0"),
                StoreBusinessStatus.OPEN, true, true, "10:00–22:00"), List.of());
        when(catalogService.getMenu(1L)).thenReturn(menu);
        MenuCacheService service = new MenuCacheService(
                redis, new ObjectMapper(), catalogService, new MenuCacheStats(new SimpleMeterRegistry()),
                60, 0, 10, true);

        MenuCacheService.CachedMenu result = service.getMenu(1L);

        assertThat(result.cacheStatus()).isEqualTo("BYPASS");
        assertThat(result.menu()).isSameAs(menu);
    }
}
