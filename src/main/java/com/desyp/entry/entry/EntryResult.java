package com.desyp.entry.entry;

/**
 * @param outcome       처리 결과
 * @param receiptNumber 발급(또는 재전달)한 접수 번호. 간격 제한이면 0
 */
public record EntryResult(Outcome outcome, long receiptNumber) {

    public enum Outcome {
        /** 새 응모 */
        CREATED,
        /** 같은 requestId 재요청 — 처음 번호를 돌려준다 */
        REPLAYED,
        /** 같은 Instagram 아이디의 요청 간격 제한 */
        RATE_LIMITED
    }

    static EntryResult created(long receiptNumber) {
        return new EntryResult(Outcome.CREATED, receiptNumber);
    }

    static EntryResult replayed(long receiptNumber) {
        return new EntryResult(Outcome.REPLAYED, receiptNumber);
    }

    static EntryResult rateLimited() {
        return new EntryResult(Outcome.RATE_LIMITED, 0);
    }
}
