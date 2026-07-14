package com.hellblade.poepricer.collector.config;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("poe.trade-dataset")
public record TradeDatasetProperties(
        boolean enabled,
        URI baseUrl,
        String userAgent,
        String league,
        String runKey,
        String category,
        String availability,
        int targetSize,
        int fetchSize,
        Duration searchRequestDelay,
        Duration fetchRequestDelay,
        Duration connectTimeout,
        Duration readTimeout,
        String priceCurrency,
        BigDecimal minimumPrice,
        BigDecimal maximumPrice
) {

    public void validate() {
        Objects.requireNonNull(baseUrl, "POE_TRADE_BASE_URL must be configured");
        requireText(userAgent, "POE_USER_AGENT");
        requireText(league, "POE_TRADE_LEAGUE");
        requireText(runKey, "POE_TRADE_DATASET_RUN_KEY");
        requireText(category, "POE_TRADE_DATASET_CATEGORY");
        requireText(availability, "POE_TRADE_DATASET_AVAILABILITY");
        requireText(priceCurrency, "POE_TRADE_DATASET_PRICE_CURRENCY");

        if (targetSize < 1 || targetSize > 10_000) {
            throw new IllegalStateException(
                    "POE_TRADE_DATASET_TARGET_SIZE must be between 1 and 10000"
            );
        }
        if (fetchSize < 1 || fetchSize > 10) {
            throw new IllegalStateException(
                    "POE_TRADE_DATASET_FETCH_SIZE must be between 1 and 10"
            );
        }
        requirePositive(searchRequestDelay, "POE_TRADE_DATASET_SEARCH_REQUEST_DELAY");
        requirePositive(fetchRequestDelay, "POE_TRADE_DATASET_FETCH_REQUEST_DELAY");
        requirePositive(connectTimeout, "POE_TRADE_CONNECT_TIMEOUT");
        requirePositive(readTimeout, "POE_TRADE_READ_TIMEOUT");

        if (minimumPrice == null || minimumPrice.signum() <= 0) {
            throw new IllegalStateException(
                    "POE_TRADE_DATASET_MINIMUM_PRICE must be positive"
            );
        }
        if (minimumPrice.compareTo(BigDecimal.TEN) >= 0) {
            throw new IllegalStateException(
                    "POE_TRADE_DATASET_MINIMUM_PRICE must be less than 10"
            );
        }
        if (maximumPrice == null || maximumPrice.compareTo(minimumPrice) <= 0) {
            throw new IllegalStateException(
                    "POE_TRADE_DATASET_MAXIMUM_PRICE must be greater than the minimum price"
            );
        }
    }

    private void requireText(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be configured");
        }
    }

    private void requirePositive(Duration value, String environmentVariable) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(environmentVariable + " must be positive");
        }
    }
}
