package com.hellblade.poepricer.collector.trade.client;

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
    private static final List<String> LOGGED_HEADERS = List.of(
            "X-Rate-Limit-Policy",
            "X-Rate-Limit-Rules",
            "X-Rate-Limit-IP",
            "X-Rate-Limit-IP-State",
            "X-Rate-Limit-Account",
            "X-Rate-Limit-Account-State",
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
        LOGGED_HEADERS.forEach(header -> {
            String value = response.getHeaders().getFirst(header);
            if (value != null) {
                presentHeaders.put(header, value);
            }
        });

        if (!presentHeaders.isEmpty()) {
            LOGGER.info(
                    "Trade API rate state: status={}, headers={}",
                    response.getStatusCode().value(),
                    presentHeaders
            );
        }
        return response;
    }
}
