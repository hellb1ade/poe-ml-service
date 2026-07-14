package com.hellblade.poepricer.collector.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hellblade.poepricer.collector.config.PoeApiProperties;

class OAuthTokenProviderTest {

    @Test
    void requestsAndCachesAClientCredentialsToken() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://www.pathofexile.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OAuthTokenProvider provider = new OAuthTokenProvider(
                builder.build(),
                properties(null)
        );

        server.expect(once(), requestTo("https://www.pathofexile.com/oauth/token"))
                .andExpect(method(POST))
                .andExpect(content().formDataContains(Map.of(
                        "client_id", "client-id",
                        "client_secret", "client-secret",
                        "grant_type", "client_credentials",
                        "scope", "service:psapi"
                )))
                .andRespond(withSuccess("""
                        {"access_token":"service-token","token_type":"bearer","scope":"service:psapi"}
                        """, APPLICATION_JSON));

        assertEquals("service-token", provider.accessToken());
        assertEquals("service-token", provider.accessToken());
        server.verify();
    }

    private PoeApiProperties properties(String accessToken) {
        return new PoeApiProperties(
                URI.create("https://api.pathofexile.com"),
                URI.create("https://www.pathofexile.com"),
                "client-id",
                "client-secret",
                accessToken,
                "test@example.com",
                "0.1.0",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
    }
}
