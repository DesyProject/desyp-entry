package com.desyp.entry.entry;

/**
 * @param outcome       처리 결과
 * @param rank 발급(또는 재전달)한 접수 순번. 간격 제한이면 0
 */
public record EntryResult(Outcome outcome, long rank) {

    public enum Outcome {
        /** 새 응모 */
        CREATED,
        /** 같은 requestId 재요청 — 처음 번호를 돌려준다 */
        REPLAYED,
        /** 같은 Instagram 아이디의 요청 간격 제한 */
        RATE_LIMITED
    }

    static EntryResult created(long rank) {
        return new EntryResult(Outcome.CREATED, rank);
    }

    static EntryResult replayed(long rank) {
        return new EntryResult(Outcome.REPLAYED, rank);
    }

    static EntryResult rateLimited() {
        return new EntryResult(Outcome.RATE_LIMITED, 0);
    }

    boolean created() {
        return outcome == Outcome.CREATED;
    }
}
