package com.desyp.entry.entry;

import com.desyp.entry.error.EntryException;
import com.desyp.entry.event.EventPhase;
import com.desyp.entry.event.EventStatusService;
import com.desyp.entry.metrics.EntryMetrics;
import com.desyp.entry.metrics.EntryOutcome;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryService {

    private static final Pattern INSTAGRAM_ID = Pattern.compile("^[a-z0-9._]{1,30}$");

    private final EventStatusService eventStatusService;
    private final EntryStore entryStore;
    private final Clock clock;
    private final EntryMetrics metrics;

    public EntryResult submit(EntryRequest request) {
        if (eventStatusService.currentPhase() != EventPhase.OPEN) {
            metrics.count(EntryOutcome.CLOSED);
            throw new EntryException(HttpStatus.GONE, "지금은 응모 시간이 아닙니다.");
        }
        String requestId = request.requestId().trim().toLowerCase(Locale.ROOT);
        if (!isUuid(requestId)) {
            metrics.count(EntryOutcome.INVALID);
            throw new EntryException(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
        }
        String instagramId = normalizeInstagramId(request.instagramId());
        if (!INSTAGRAM_ID.matcher(instagramId).matches()) {
            metrics.count(EntryOutcome.INVALID);
            throw new EntryException(HttpStatus.BAD_REQUEST, "Instagram 아이디 형식이 올바르지 않습니다.");
        }

        String acceptedAt = clock.instant().toString();
        EntryResult result = metrics.timeScript(
                () -> entryStore.submit(instagramId, requestId, acceptedAt));
        switch (result.outcome()) {
            case CREATED -> {
                metrics.count(EntryOutcome.CREATED);
                log.info("응모 접수 rank={}", result.rank());
            }
            case REPLAYED -> {
                metrics.count(EntryOutcome.REPLAYED);
                log.info("재요청에 기존 순번 반환 rank={}", result.rank());
            }
            case RATE_LIMITED -> {
                metrics.count(EntryOutcome.RATE_LIMITED);
                log.info("같은 Instagram 아이디 1초 제한");
                throw new EntryException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 응모해주세요.");
            }
            default -> throw new IllegalStateException("unknown outcome: " + result.outcome());
        }
        return result;
    }

    /** 프론트와 같은 규칙: 앞의 @ 제거, 소문자 */
    static String normalizeInstagramId(String raw) {
        return raw.trim().replaceFirst("^@+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
