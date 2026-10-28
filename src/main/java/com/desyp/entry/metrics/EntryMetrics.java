package com.desyp.entry.metrics;

import com.desyp.entry.entry.EntryStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 응모 지표. Prometheus 이름은 desyp_entry_requests_total, desyp_entry_redis_script_seconds, desyp_entry_last_receipt.
 * Instagram 아이디 같은 개인정보는 태그로 쓰지 않는다.
 */
@Component
public class EntryMetrics {

    private final MeterRegistry registry;
    private final Timer scriptTimer;

    public EntryMetrics(MeterRegistry registry, EntryStore entryStore) {
        this.registry = registry;
        this.scriptTimer = Timer.builder("desyp.entry.redis.script")
                .description("응모 Lua 스크립트 처리 시간")
                .publishPercentileHistogram()
                .register(registry);
        Gauge.builder("desyp.entry.last.receipt", entryStore, EntryMetrics::lastReceiptOrNaN)
                .description("지금까지 발급한 마지막 접수 번호")
                .register(registry);
    }

    /** outcome: created, replayed, rate_limited, closed, invalid, error */
    public void count(EntryOutcome outcome) {
        registry.counter("desyp.entry.requests", "outcome", outcome.tag()).increment();
    }

    public <T> T timeScript(Supplier<T> call) {
        return scriptTimer.record(call);
    }

    private static double lastReceiptOrNaN(EntryStore entryStore) {
        try {
            return entryStore.lastReceiptNumber();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
