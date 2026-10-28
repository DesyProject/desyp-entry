package com.desyp.entry.error;

import org.springframework.http.HttpStatus;

public class EntryException extends RuntimeException {

    private final HttpStatus status;

    public EntryException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
