package com.hellblade.poepricer.api.client.trade;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

final class TradeRateLimitLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TradeRateLimitLoggingInterceptor.class);
    private static final List<String> RATE_LIMIT_HEADERS = List.of(
            "X-Rate-Limit-Policy",
            "X-Rate-Limit-Rules",
            "X-Rate-Limit-IP",
            "X-Rate-Limit-IP-State",
            "X-Rate-Limit-Account",
            "X-Rate-Limit-Account-State",
            "X-Rate-Limit-Client",
            "X-Rate-Limit-Client-State",
            HttpHeaders.RETRY_AFTER
    );

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        Map<String, String> presentHeaders = new LinkedHashMap<>();
        RATE_LIMIT_HEADERS.forEach(header -> {
            String value = response.getHeaders().getFirst(header);
            if (value != null) {
                presentHeaders.put(header, value);
            }
        });

        if (!presentHeaders.isEmpty()) {
            if (response.getStatusCode().value() == 429) {
                LOGGER.warn("PoE Trade rate limit reached: {}", presentHeaders);
            } else {
                LOGGER.debug("PoE Trade rate limit state: {}", presentHeaders);
            }
        }

        return response;
    }
}
