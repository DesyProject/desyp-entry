package com.desyp.entry.entry;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param requestId                응모마다 프론트가 만드는 UUID. 재시도에서만 같은 값을 다시 보낸다
 * @param instagramId              당첨 DM 연락용. 서버에서 다시 정규화·검증한다
 * @param ageConfirmed             [필수] 만 14세 이상
 * @param agreeInstagramCollection [필수] Instagram 아이디 수집·이용 동의
 */
public record EntryRequest(
        @NotBlank String requestId,
        @NotBlank String instagramId,
        @NotNull @AssertTrue Boolean ageConfirmed,
        @NotNull @AssertTrue Boolean agreeInstagramCollection) {
}
