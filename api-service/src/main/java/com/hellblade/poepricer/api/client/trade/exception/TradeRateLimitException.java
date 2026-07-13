package com.hellblade.poepricer.api.client.trade.exception;

import java.time.Duration;

public class TradeRateLimitException extends TradeApiException {

    private final Duration retryAfter;

    public TradeRateLimitException(Duration retryAfter, Throwable cause) {
        super("PoE Trade rate limit exceeded", 429, cause);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
