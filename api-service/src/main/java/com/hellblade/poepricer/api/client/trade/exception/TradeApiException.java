package com.hellblade.poepricer.api.client.trade.exception;

public class TradeApiException extends RuntimeException {

    private final Integer statusCode;

    public TradeApiException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public TradeApiException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
