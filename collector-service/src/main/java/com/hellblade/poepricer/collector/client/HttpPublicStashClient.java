package com.hellblade.poepricer.collector.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import com.hellblade.poepricer.collector.client.dto.PublicStashResponse;
import com.hellblade.poepricer.collector.config.CollectorProperties;

@Component
@ConditionalOnProperty(prefix = "poe.collector", name = "enabled", havingValue = "true")
public class HttpPublicStashClient implements PublicStashClient {

    private final RestClient restClient;
    private final OAuthTokenProvider tokenProvider;
    private final CollectorProperties properties;

    public HttpPublicStashClient(
            @Qualifier("poeApiRestClient") RestClient restClient,
            OAuthTokenProvider tokenProvider,
            CollectorProperties properties
    ) {
        this.restClient = restClient;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
    }

    @Override
    public PublicStashResponse getNextPage(String nextChangeId) {
        return execute(nextChangeId, true);
    }

    private PublicStashResponse execute(String nextChangeId, boolean allowTokenRefresh) {
        try {
            PublicStashResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/public-stash-tabs");
                        if (!"pc".equals(properties.normalizedRealm())) {
                            uriBuilder.pathSegment(properties.normalizedRealm());
                        }
                        if (nextChangeId != null && !nextChangeId.isBlank()) {
                            uriBuilder.queryParam("id", nextChangeId);
                        }
                        return uriBuilder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.accessToken())
                    .retrieve()
                    .requiredBody(PublicStashResponse.class);
            if (response.nextChangeId() == null || response.nextChangeId().isBlank()) {
                throw new PoeApiException(
                        "Public Stash response did not contain next_change_id",
                        null,
                        null,
                        false,
                        null
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 && allowTokenRefresh && tokenProvider.canRefresh()) {
                tokenProvider.invalidate();
                return execute(nextChangeId, false);
            }

            Duration retryAfter = parseRetryAfter(exception.getResponseHeaders());
            boolean retryable = status == 408 || status == 429 || status >= 500;
            throw new PoeApiException(
                    "Public Stash request failed with HTTP " + status,
                    status,
                    retryAfter,
                    retryable,
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new PoeApiException(
                    "Public Stash request failed",
                    null,
                    null,
                    true,
                    exception
            );
        } catch (RestClientException exception) {
            throw new PoeApiException(
                    "Public Stash response could not be processed",
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
