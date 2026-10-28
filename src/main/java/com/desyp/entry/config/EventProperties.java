package com.desyp.entry.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 메인 이벤트 운영 값. 날짜가 정해지면 환경 변수로만 바꾼다.
 *
 * @param startAt       응모 시작 시각(포함)
 * @param endAt         응모 종료 시각(미포함)
 * @param randomNumber  이벤트 전에 미리 정해 둔 랜덤 당첨 번호. 응모 수가 이보다 적으면 마지막 접수 번호가 당첨
 * @param allowedOrigin CORS를 허용할 프론트 주소
 * @param rateLimit     같은 Instagram 아이디의 최소 요청 간격
 * @param requestTtl    같은 requestId 재요청에 처음 번호를 돌려주는 기간
 */
@Validated
@ConfigurationProperties("desyp.event")
public record EventProperties(
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        @Min(1) int randomNumber,
        @NotBlank String allowedOrigin,
        @NotNull Duration rateLimit,
        @NotNull Duration requestTtl) {
}
