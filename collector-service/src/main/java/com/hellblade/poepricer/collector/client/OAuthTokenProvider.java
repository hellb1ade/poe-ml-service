package com.hellblade.poepricer.collector.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import com.hellblade.poepricer.collector.client.dto.OAuthTokenResponse;
import com.hellblade.poepricer.collector.config.PoeApiProperties;

@Component
@ConditionalOnProperty(prefix = "poe.collector", name = "enabled", havingValue = "true")
public class OAuthTokenProvider {

    private final RestClient restClient;
    private final PoeApiProperties properties;
    private volatile String cachedToken;

    public OAuthTokenProvider(
            @Qualifier("poeOAuthRestClient") RestClient restClient,
            PoeApiProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public String accessToken() {
        if (properties.hasAccessToken()) {
            return properties.accessToken().trim();
        }

        String token = cachedToken;
        if (token != null) {
            return token;
        }

        synchronized (this) {
            if (cachedToken == null) {
                cachedToken = requestToken();
            }
            return cachedToken;
        }
    }

    public void invalidate() {
        if (!properties.hasAccessToken()) {
            cachedToken = null;
        }
    }

    public boolean canRefresh() {
        return !properties.hasAccessToken();
    }

    private String requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "client_credentials");
        form.add("scope", "service:psapi");

        try {
            OAuthTokenResponse response = restClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .requiredBody(OAuthTokenResponse.class);
            if (response.accessToken() == null || response.accessToken().isBlank()) {
                throw new PoeApiException(
                        "PoE OAuth response did not contain an access token",
                        null,
                        null,
                        false,
                        null
                );
            }
            return response.accessToken();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new PoeApiException(
                    "PoE OAuth token request failed with HTTP " + status,
                    status,
                    parseRetryAfter(exception.getResponseHeaders()),
                    status == 408 || status == 429 || status >= 500,
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new PoeApiException(
                    "PoE OAuth token request failed",
                    null,
                    null,
                    true,
                    exception
            );
        } catch (RestClientException exception) {
            throw new PoeApiException(
                    "PoE OAuth response could not be processed",
                    null,
                    null,
                    false,
                    exception
            );
        }
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
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
}
