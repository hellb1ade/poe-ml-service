package com.hellblade.poepricer.api.client.trade;

import java.net.URI;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("poe.trade")
public record TradeClientProperties(
        boolean enabled,
        URI baseUrl,
        String userAgent,
        int maxFetchSize
) {

    public TradeClientProperties {
        Objects.requireNonNull(baseUrl, "Trade API base URL is required");
        if (maxFetchSize < 1 || maxFetchSize > 10) {
            throw new IllegalArgumentException("Trade fetch size must be between 1 and 10");
        }
    }
}
