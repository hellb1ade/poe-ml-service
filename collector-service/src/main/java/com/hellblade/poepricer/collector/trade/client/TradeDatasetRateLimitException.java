package com.hellblade.poepricer.collector.trade.client;

import java.time.Duration;

public class TradeDatasetRateLimitException extends TradeDatasetApiException {

    private final Duration retryAfter;

    public TradeDatasetRateLimitException(Duration retryAfter, Throwable cause) {
        super("Trade API rate limit reached", 429, cause);
        this.retryAfter = retryAfter == null ? Duration.ZERO : retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
