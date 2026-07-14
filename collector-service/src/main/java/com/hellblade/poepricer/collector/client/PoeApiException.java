package com.hellblade.poepricer.collector.client;

import java.time.Duration;

public class PoeApiException extends RuntimeException {

    private final Integer statusCode;
    private final Duration retryAfter;
    private final boolean retryable;

    public PoeApiException(
            String message,
            Integer statusCode,
            Duration retryAfter,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter == null ? Duration.ZERO : retryAfter;
        this.retryable = retryable;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public boolean retryable() {
        return retryable;
    }
}
