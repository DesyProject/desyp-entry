package com.desyp.entry.entry;

import com.desyp.entry.config.EventProperties;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

/**
 * Redis 하나에서 접수 번호를 발급한다. 번호 중복을 막으려고 Redis를 여러 대로 나누지 않는다.
 * 간격 제한 확인, 번호 증가, 응모 기록(Stream) 저장을 Lua 스크립트 한 번으로 처리한다.
 * Stream을 DB에 옮기는 일은 이 서비스 밖(워커)의 책임이다.
 */
@Component
public class EntryStore {

    static final String SEQUENCE_KEY = "entry:seq";
    static final String STREAM_KEY = "entry:stream";
    private static final String REQUEST_KEY_PREFIX = "entry:req:";
    private static final String RATE_KEY_PREFIX = "entry:rate:";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ENTRY_SCRIPT = loadScript();

    private final StringRedisTemplate redis;
    private final EventProperties properties;

    public EntryStore(StringRedisTemplate redis, EventProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @SuppressWarnings("rawtypes")
    private static RedisScript<List> loadScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/entry.lua")));
        script.setResultType(List.class);
        return script;
    }

    public EntryResult submit(String instagramId, String requestId, String clientId, long submittedAtMillis) {
        List<?> result = redis.execute(
                ENTRY_SCRIPT,
                List.of(REQUEST_KEY_PREFIX + requestId, RATE_KEY_PREFIX + instagramId, SEQUENCE_KEY, STREAM_KEY),
                String.valueOf(properties.rateLimit().toMillis()),
                String.valueOf(properties.requestTtl().toMillis()),
                instagramId,
                requestId,
                clientId,
                String.valueOf(submittedAtMillis));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("entry script returned an unexpected result");
        }
        long code = ((Number) result.get(0)).longValue();
        long receiptNumber = ((Number) result.get(1)).longValue();
        return switch ((int) code) {
            case 0 -> EntryResult.created(receiptNumber);
            case 1 -> EntryResult.replayed(receiptNumber);
            case 2 -> EntryResult.rateLimited();
            default -> throw new IllegalStateException("unknown entry script code: " + code);
        };
    }

    /** 지금까지 발급한 마지막 접수 번호. 응모가 없으면 0 */
    public long lastReceiptNumber() {
        String value = redis.opsForValue().get(SEQUENCE_KEY);
        return value == null ? 0 : Long.parseLong(value);
    }
}
