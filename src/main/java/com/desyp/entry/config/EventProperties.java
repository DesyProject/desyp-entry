package com.desyp.entry.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 메인 이벤트 운영 값. 날짜가 정해지면 환경 변수로만 바꾼다.
 *
 * @param startAt       응모 시작 시각(포함)
 * @param endAt         응모 종료 시각(미포함)
 * @param winningNumbers 이벤트 전에 미리 정해 둔 당첨 접수 번호 18개
 * @param eventId       내부 메시지와 Redis 키에 사용하는 이벤트 식별자
 * @param allowedOrigins CORS를 허용할 프론트 주소 목록
 * @param rateLimit     같은 Instagram 아이디의 최소 요청 간격
 * @param requestTtl    같은 requestId 재요청에 처음 번호를 돌려주는 기간
 */
@Validated
@ConfigurationProperties("desyp.event")
public record EventProperties(
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        @NotEmpty List<Long> winningNumbers,
        @NotBlank String eventId,
        @NotEmpty List<@NotBlank String> allowedOrigins,
        @NotNull Duration rateLimit,
        @NotNull Duration requestTtl) {

    public EventProperties {
        if (winningNumbers == null || winningNumbers.size() != 18) {
            throw new IllegalArgumentException("winningNumbers must contain exactly 18 numbers");
        }
        if (winningNumbers.stream().anyMatch(number -> number == null || number < 1)) {
            throw new IllegalArgumentException("winningNumbers must contain only positive numbers");
        }
        if (!winningNumbers.contains(1L)) {
            throw new IllegalArgumentException("winningNumbers must contain 1");
        }
        if (new HashSet<>(winningNumbers).size() != winningNumbers.size()) {
            throw new IllegalArgumentException("winningNumbers must not contain duplicates");
        }
        winningNumbers = List.copyOf(winningNumbers);
    }
}
