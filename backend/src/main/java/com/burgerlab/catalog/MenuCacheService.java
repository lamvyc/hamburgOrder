package com.burgerlab.catalog;

import com.burgerlab.catalog.dto.MenuResponse;
import com.burgerlab.common.error.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MenuCacheService {

    private static final Logger log = LoggerFactory.getLogger(MenuCacheService.class);
    private static final String KEY_PREFIX = "menu:v1:";
    private static final String MISSING = "__MISSING__";
    private static final long WAIT_MILLIS = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CatalogService catalogService;
    private final MenuCacheStats stats;
    private final long ttlSeconds;
    private final long ttlJitterSeconds;
    private final long negativeTtlSeconds;
    private final boolean enabled;
    private final ConcurrentHashMap<Long, CompletableFuture<CachedMenu>> inFlight =
            new ConcurrentHashMap<>();

    public MenuCacheService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            CatalogService catalogService,
            MenuCacheStats stats,
            @Value("${app.cache.menu.ttl-seconds:60}") long ttlSeconds,
            @Value("${app.cache.menu.ttl-jitter-seconds:15}") long ttlJitterSeconds,
            @Value("${app.cache.menu.negative-ttl-seconds:10}") long negativeTtlSeconds,
            @Value("${app.cache.menu.enabled:true}") boolean enabled
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.catalogService = catalogService;
        this.stats = stats;
        this.ttlSeconds = ttlSeconds;
        this.ttlJitterSeconds = Math.max(0, ttlJitterSeconds);
        this.negativeTtlSeconds = Math.max(1, negativeTtlSeconds);
        this.enabled = enabled;
    }

    public CachedMenu getMenu(long storeId) {
        if (!enabled) {
            return new CachedMenu(catalogService.getMenu(storeId), "BYPASS");
        }

        try {
            CachedMenu hit = readCache(storeId);
            if (hit != null) {
                return hit;
            }
        } catch (ResourceNotFoundException missing) {
            throw missing;
        } catch (RuntimeException redisFailure) {
            log.warn("menu_cache_read_failed storeId={} error={}",
                    storeId, redisFailure.getClass().getSimpleName());
            stats.bypass();
            return databaseBypass(storeId);
        }

        CompletableFuture<CachedMenu> mine = new CompletableFuture<>();
        CompletableFuture<CachedMenu> leader = inFlight.putIfAbsent(storeId, mine);
        if (leader != null) {
            stats.stampedeWait();
            try {
                return leader.get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                stats.bypass();
                return databaseBypass(storeId);
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof ResourceNotFoundException missing) {
                    stats.negative();
                    throw missing;
                }
                stats.bypass();
                return databaseBypass(storeId);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                stats.bypass();
                return databaseBypass(storeId);
            }
        }

        try {
            // Another instance may have filled the cache between our miss and
            // winning the local single-flight slot.
            CachedMenu doubleCheck = readCache(storeId);
            if (doubleCheck != null) {
                mine.complete(doubleCheck);
                return doubleCheck;
            }

            MenuResponse menu;
            try {
                menu = catalogService.getMenu(storeId);
            } catch (ResourceNotFoundException missing) {
                writeNegative(storeId);
                stats.negative();
                mine.completeExceptionally(missing);
                throw missing;
            }

            writeCache(storeId, menu);
            stats.miss();
            CachedMenu result = new CachedMenu(menu, "MISS");
            mine.complete(result);
            return result;
        } catch (RuntimeException failure) {
            mine.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(storeId, mine);
        }
    }

    public void evictAfterCommit(long storeId) {
        if (!enabled) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(storeId);
                }
            });
        } else {
            evict(storeId);
        }
    }

    private CachedMenu readCache(long storeId) {
        String key = key(storeId);
        String cached = redis.opsForValue().get(key);
        if (cached == null) {
            return null;
        }
        if (MISSING.equals(cached)) {
            stats.negative();
            throw new ResourceNotFoundException("STORE_NOT_FOUND", "门店不存在");
        }
        try {
            MenuResponse menu = objectMapper.readValue(cached, MenuResponse.class);
            stats.hit();
            return new CachedMenu(menu, "HIT");
        } catch (JsonProcessingException broken) {
            redis.delete(key);
            return null;
        }
    }

    private CachedMenu databaseBypass(long storeId) {
        return new CachedMenu(catalogService.getMenu(storeId), "BYPASS");
    }

    private void writeCache(long storeId, MenuResponse menu) {
        long ttl = ttlSeconds;
        if (ttlJitterSeconds > 0) {
            ttl += ThreadLocalRandom.current().nextLong(ttlJitterSeconds + 1);
        }
        try {
            redis.opsForValue().set(
                    key(storeId), objectMapper.writeValueAsString(menu), Duration.ofSeconds(ttl));
        } catch (RuntimeException | JsonProcessingException cacheFailure) {
            log.warn("menu_cache_write_failed storeId={} error={}",
                    storeId, cacheFailure.getClass().getSimpleName());
        }
    }

    private void writeNegative(long storeId) {
        try {
            redis.opsForValue().set(
                    key(storeId), MISSING, Duration.ofSeconds(negativeTtlSeconds));
        } catch (RuntimeException cacheFailure) {
            log.warn("menu_negative_cache_write_failed storeId={} error={}",
                    storeId, cacheFailure.getClass().getSimpleName());
        }
    }

    private void evict(long storeId) {
        try {
            redis.delete(key(storeId));
        } catch (RuntimeException redisFailure) {
            log.warn("menu_cache_evict_failed storeId={} error={}",
                    storeId, redisFailure.getClass().getSimpleName());
        }
    }

    private String key(long storeId) {
        return KEY_PREFIX + storeId;
    }

    public record CachedMenu(MenuResponse menu, String cacheStatus) {
    }
}
