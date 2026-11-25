package com.desyp.entry.event;

import com.desyp.entry.config.EventProperties;
import com.desyp.entry.entry.EntryStore;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
        List<Long> winningNumbers = phase == EventPhase.CLOSED ? announcedWinningNumbers() : null;
        return new EventStatusResponse(
                phase,
                properties.startAt(),
                properties.endAt(),
                OffsetDateTime.ofInstant(now, ZoneOffset.ofHours(9)),
                winningNumbers);
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

    private List<Long> announcedWinningNumbers() {
        long lastRank = entryStore.lastRank();
        if (lastRank == 0) {
            return null;
        }
        return properties.winningNumbers().stream()
                .filter(number -> number <= lastRank)
                .toList();
    }
}
