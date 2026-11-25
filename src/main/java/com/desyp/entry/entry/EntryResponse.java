package com.desyp.entry.entry;

public record EntryResponse(String requestId, long rank, long receiptNumber, boolean created) {
}
