package com.desyp.entry.event;

import java.time.OffsetDateTime;

/**
 * @param randomNumber 이벤트 종료 뒤에만 채운다. 그 전과 응모가 0건이면 null
 */
public record EventStatusResponse(
        EventPhase phase,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime serverTime,
        Long randomNumber) {
}
