package com.desyp.entry.event;

import com.desyp.entry.config.EventProperties;
import com.desyp.entry.entry.EntryStore;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventStatusService {

    private final EventProperties properties;
    private final EntryStore entryStore;
    private final Clock clock;

    public EventPhase currentPhase() {
        return phaseAt(clock.instant());
    }

    public EventStatusResponse status() {
        Instant now = clock.instant();
        EventPhase phase = phaseAt(now);
        Long randomNumber = phase == EventPhase.CLOSED ? announcedRandomNumber() : null;
        return new EventStatusResponse(
                phase,
                properties.startAt(),
                properties.endAt(),
                OffsetDateTime.ofInstant(now, ZoneOffset.ofHours(9)),
                randomNumber);
    }

    private EventPhase phaseAt(Instant now) {
        if (now.isBefore(properties.startAt().toInstant())) {
            return EventPhase.BEFORE;
        }
        if (now.isBefore(properties.endAt().toInstant())) {
            return EventPhase.OPEN;
        }
        return EventPhase.CLOSED;
    }

    /** 응모 수가 정해 둔 번호에 못 미치면 마지막 접수 번호가 당첨이다 */
    private Long announcedRandomNumber() {
        long lastReceipt = entryStore.lastReceiptNumber();
        if (lastReceipt == 0) {
            return null;
        }
        return Math.min(properties.randomNumber(), lastReceipt);
    }
}
