package com.hellblade.poepricer.collector.trade.client;

public class TradeDatasetApiException extends RuntimeException {

    private final Integer statusCode;

    public TradeDatasetApiException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
