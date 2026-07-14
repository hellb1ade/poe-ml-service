package com.hellblade.poepricer.collector.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("poe.api")
public record PoeApiProperties(
        URI baseUrl,
        URI oauthBaseUrl,
        String clientId,
        String clientSecret,
        String accessToken,
        String contact,
        String version,
        Duration connectTimeout,
        Duration readTimeout
) {

    public void validate() {
        require(baseUrl, "POE_API_BASE_URL");
        require(oauthBaseUrl, "POE_OAUTH_BASE_URL");
        requireText(clientId, "POE_CLIENT_ID");
        requireText(contact, "POE_CONTACT");
        requireText(version, "POE_APP_VERSION");
        requirePositive(connectTimeout, "POE_CONNECT_TIMEOUT");
        requirePositive(readTimeout, "POE_READ_TIMEOUT");

        if (!hasAccessToken()) {
            requireText(clientSecret, "POE_CLIENT_SECRET");
        }
    }

    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }

    public String userAgent() {
        return "OAuth " + clientId.trim() + "/" + version.trim()
                + " (contact: " + contact.trim() + ")";
    }

    private static void require(Object value, String environmentVariable) {
        if (value == null) {
            throw new IllegalStateException(environmentVariable + " must be configured");
        }
    }

    private static void requireText(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be configured");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalStateException(environmentVariable + " contains invalid characters");
        }
    }

    private static void requirePositive(Duration value, String environmentVariable) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(environmentVariable + " must be positive");
        }
    }
}
