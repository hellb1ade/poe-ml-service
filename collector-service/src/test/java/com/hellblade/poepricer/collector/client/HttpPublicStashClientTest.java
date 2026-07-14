package com.hellblade.poepricer.collector.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hellblade.poepricer.collector.client.dto.PublicStashResponse;
import com.hellblade.poepricer.collector.config.CollectorProperties;

class HttpPublicStashClientTest {

    private MockRestServiceServer server;
    private HttpPublicStashClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.pathofexile.com");
        server = MockRestServiceServer.bindTo(builder).build();
        OAuthTokenProvider tokenProvider = mock(OAuthTokenProvider.class);
        when(tokenProvider.accessToken()).thenReturn("service-token");

        client = new HttpPublicStashClient(
                builder.build(),
                tokenProvider,
                new CollectorProperties(
                        true,
                        "pc",
                        "Mirage",
                        "test-stream",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5),
                        Duration.ofMinutes(1),
                        true
                )
        );
    }

    @Test
    void requestsTheNextPageWithBearerToken() {
        server.expect(once(), requestTo(
                        "https://api.pathofexile.com/public-stash-tabs?id=cursor-1"
                ))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess("""
                        {
                          "next_change_id": "cursor-2",
                          "stashes": [{"id":"stash-1","public":false}]
                        }
                        """, APPLICATION_JSON));

        PublicStashResponse response = client.getNextPage("cursor-1");

        assertEquals("cursor-2", response.nextChangeId());
        assertEquals(1, response.stashes().size());
        server.verify();
    }

    @Test
    void mapsRetryAfterFromRateLimitResponse() {
        server.expect(once(), requestTo(
                        "https://api.pathofexile.com/public-stash-tabs?id=cursor-1"
                ))
                .andRespond(withStatus(TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "11"));

        PoeApiException exception = assertThrows(
                PoeApiException.class,
                () -> client.getNextPage("cursor-1")
        );

        assertEquals(429, exception.statusCode());
        assertEquals(Duration.ofSeconds(11), exception.retryAfter());
        server.verify();
    }
}
