package com.burgerlab.catalog;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class MenuCacheStats {

    private final LongAdder hit = new LongAdder();
    private final LongAdder miss = new LongAdder();
    private final LongAdder bypass = new LongAdder();
    private final LongAdder negative = new LongAdder();
    private final LongAdder stampedeWait = new LongAdder();

    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter bypassCounter;
    private final Counter negativeCounter;
    private final Counter stampedeCounter;

    public MenuCacheStats(MeterRegistry registry) {
        hitCounter = counter(registry, "hit");
        missCounter = counter(registry, "miss");
        bypassCounter = counter(registry, "bypass");
        negativeCounter = counter(registry, "negative");
        stampedeCounter = counter(registry, "stampede_wait");
    }

    public void hit() {
        hit.increment();
        hitCounter.increment();
    }

    public void miss() {
        miss.increment();
        missCounter.increment();
    }

    public void bypass() {
        bypass.increment();
        bypassCounter.increment();
    }

    public void negative() {
        negative.increment();
        negativeCounter.increment();
    }

    public void stampedeWait() {
        stampedeWait.increment();
        stampedeCounter.increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(hit.sum(), miss.sum(), bypass.sum(), negative.sum(), stampedeWait.sum());
    }

    private Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("burger.menu.cache.requests")
                .tag("result", result)
                .description("Menu cache request outcomes")
                .register(registry);
    }

    public record Snapshot(long hit, long miss, long bypass, long negative, long stampedeWait) {
    }
}
