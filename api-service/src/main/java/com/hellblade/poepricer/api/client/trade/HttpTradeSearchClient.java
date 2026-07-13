package com.hellblade.poepricer.api.client.trade;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.hellblade.poepricer.api.client.trade.dto.TradeFetchResponse;
import com.hellblade.poepricer.api.client.trade.dto.TradeSearchRequest;
import com.hellblade.poepricer.api.client.trade.dto.TradeSearchResponse;
import com.hellblade.poepricer.api.client.trade.exception.TradeApiException;
import com.hellblade.poepricer.api.client.trade.exception.TradeRateLimitException;

@Component
@ConditionalOnProperty(prefix = "poe.trade", name = "enabled", havingValue = "true")
public class HttpTradeSearchClient implements TradeSearchClient {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private final RestClient restClient;
    private final int maxFetchSize;

    public HttpTradeSearchClient(RestClient.Builder builder, TradeClientProperties properties) {
        if (properties.userAgent() == null || properties.userAgent().isBlank()) {
            throw new IllegalStateException(
                    "POE_USER_AGENT must be configured when POE_TRADE_ENABLED=true"
            );
        }

        this.maxFetchSize = properties.maxFetchSize();
        this.restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .requestInterceptor(new TradeRateLimitLoggingInterceptor())
                .build();
    }

    @Override
    public TradeSearchResponse search(String league, TradeSearchRequest request) {
        requireText(league, "League");
        Objects.requireNonNull(request, "Trade search request is required");

        return execute("Trade search", () -> restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/trade/search/")
                        .pathSegment(league)
                        .build())
                .body(request)
                .retrieve()
                .body(TradeSearchResponse.class));
    }

    @Override
    public TradeFetchResponse fetch(String searchId, List<String> listingIds) {
        requireSafeId(searchId, "Search ID");
        Objects.requireNonNull(listingIds, "Listing IDs are required");

        List<String> ids = List.copyOf(listingIds);
        if (ids.isEmpty() || ids.size() > maxFetchSize) {
            throw new IllegalArgumentException(
                    "Listing IDs must contain between 1 and " + maxFetchSize + " entries"
            );
        }
        ids.forEach(id -> requireSafeId(id, "Listing ID"));

        String joinedIds = String.join(",", ids);
        return execute("Trade fetch", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/trade/fetch/")
                        .pathSegment(joinedIds)
                        .queryParam("query", searchId)
                        .build())
                .retrieve()
                .body(TradeFetchResponse.class));
    }

    private <T> T execute(String operation, Supplier<T> request) {
        try {
            T response = request.get();
            if (response == null) {
                throw new TradeApiException(operation + " returned an empty response", null);
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new TradeRateLimitException(parseRetryAfter(exception), exception);
            }
            throw new TradeApiException(
                    operation + " failed with HTTP " + exception.getStatusCode().value(),
                    exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new TradeApiException(operation + " request failed", null, exception);
        }
    }

    private Duration parseRetryAfter(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null) {
            return Duration.ZERO;
        }

        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(retryAfter)));
        } catch (NumberFormatException ignored) {
            return Duration.ZERO;
        }
    }

    private void requireSafeId(String value, String fieldName) {
        requireText(value, fieldName);
        if (!SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported characters");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
