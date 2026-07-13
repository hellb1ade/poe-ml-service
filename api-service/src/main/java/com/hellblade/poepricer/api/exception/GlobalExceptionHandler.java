package com.hellblade.poepricer.api.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hellblade.poepricer.api.client.trade.exception.TradeApiException;
import com.hellblade.poepricer.api.client.trade.exception.TradeRateLimitException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        return new ApiErrorResponse(
                "VALIDATION_ERROR",
                "Request validation failed",
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMalformedJson() {
        return new ApiErrorResponse(
                "MALFORMED_JSON",
                "Request body is missing or malformed",
                Map.of()
        );
    }

    @ExceptionHandler(TradeRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleTradeRateLimit(
            TradeRateLimitException exception
    ) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
        if (!exception.getRetryAfter().isZero()) {
            response.header(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(exception.getRetryAfter().toSeconds())
            );
        }

        return response.body(new ApiErrorResponse(
                "TRADE_RATE_LIMITED",
                "Path of Exile Trade API rate limit reached",
                Map.of()
        ));
    }

    @ExceptionHandler(TradeApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleTradeApiError() {
        return new ApiErrorResponse(
                "TRADE_API_ERROR",
                "Path of Exile Trade API request failed",
                Map.of()
        );
    }
}
