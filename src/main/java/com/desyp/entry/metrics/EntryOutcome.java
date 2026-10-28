package com.desyp.entry.metrics;

public enum EntryOutcome {
    CREATED("created"),
    REPLAYED("replayed"),
    RATE_LIMITED("rate_limited"),
    CLOSED("closed"),
    INVALID("invalid"),
    ERROR("error");

    private final String tag;

    EntryOutcome(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
