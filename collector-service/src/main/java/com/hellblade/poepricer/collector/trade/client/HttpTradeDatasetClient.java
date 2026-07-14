package com.hellblade.poepricer.collector.trade.client;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.hellblade.poepricer.collector.config.TradeDatasetProperties;
import com.hellblade.poepricer.collector.trade.client.dto.TradeFetchResponse;
import com.hellblade.poepricer.collector.trade.client.dto.TradeSearchRequest;
import com.hellblade.poepricer.collector.trade.client.dto.TradeSearchResponse;

@Component
@ConditionalOnProperty(prefix = "poe.trade-dataset", name = "enabled", havingValue = "true")
public class HttpTradeDatasetClient implements TradeDatasetClient {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private final RestClient restClient;
    private final int maxFetchSize;

    public HttpTradeDatasetClient(
            @Qualifier("tradeDatasetRestClient") RestClient restClient,
            TradeDatasetProperties properties
    ) {
        this.restClient = restClient;
        this.maxFetchSize = properties.fetchSize();
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
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .requiredBody(TradeSearchResponse.class));
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

        return execute("Trade fetch", () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/trade/fetch/")
                        .pathSegment(String.join(",", ids))
                        .queryParam("query", searchId)
                        .build())
                .retrieve()
                .requiredBody(TradeFetchResponse.class));
    }

    private <T> T execute(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 429) {
                throw new TradeDatasetRateLimitException(parseRetryAfter(exception), exception);
            }
            throw new TradeDatasetApiException(
                    operation + " failed with HTTP " + status,
                    status,
                    exception
            );
        } catch (RestClientException exception) {
            throw new TradeDatasetApiException(operation + " request failed", null, exception);
        }
    }

    private Duration parseRetryAfter(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return Duration.ZERO;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
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
