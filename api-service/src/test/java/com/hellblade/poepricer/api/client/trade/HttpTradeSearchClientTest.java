package com.hellblade.poepricer.api.client.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hellblade.poepricer.api.client.trade.dto.TradeFetchResponse;
import com.hellblade.poepricer.api.client.trade.dto.TradeSearchRequest;
import com.hellblade.poepricer.api.client.trade.dto.TradeSearchResponse;
import com.hellblade.poepricer.api.client.trade.exception.TradeRateLimitException;

class HttpTradeSearchClientTest {

    private static final String USER_AGENT =
            "poe-pricer-test/0.1 (contact: test@example.com)";

    private MockRestServiceServer server;
    private HttpTradeSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpTradeSearchClient(
                builder,
                new TradeClientProperties(
                        true,
                        URI.create("https://www.pathofexile.com"),
                        USER_AGENT,
                        10
                )
        );
    }

    @Test
    void searchSendsExpectedRequestAndReadsListingIds() {
        server.expect(once(), requestTo(
                        "https://www.pathofexile.com/api/trade/search/Mirage"
                ))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(content().json("""
                        {
                          "query": {
                            "status": {"option": "online"},
                            "name": "Mageblood"
                          },
                          "sort": {"price": "asc"}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "id": "search-id",
                          "complexity": 3,
                          "result": ["listing-1", "listing-2"],
                          "total": 2
                        }
                        """, APPLICATION_JSON));

        TradeSearchResponse response = client.search(
                "Mirage",
                TradeSearchRequest.byName(
                        "Mageblood",
                        TradeSearchRequest.SellerStatus.ONLINE
                )
        );

        assertEquals("search-id", response.id());
        assertEquals(List.of("listing-1", "listing-2"), response.result());
        assertEquals(2, response.total());
        server.verify();
    }

    @Test
    void fetchReadsItemsAndPrices() {
        server.expect(once(), requestTo(
                        "https://www.pathofexile.com/api/trade/fetch/"
                                + "listing-1,listing-2?query=search-id"
                ))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {
                              "id": "listing-1",
                              "item": {
                                "id": "item-1",
                                "name": "Mageblood",
                                "baseType": "Heavy Belt",
                                "rarity": "Unique",
                                "ilvl": 86,
                                "implicitMods": ["4% increased Strength"],
                                "explicitMods": [
                                  {
                                    "description": "+45 to Dexterity",
                                    "hash": "stat.explicit.stat_3261801346",
                                    "mods": [
                                      {
                                        "magnitudes": [
                                          {"min": "30", "max": "50"}
                                        ]
                                      }
                                    ]
                                  }
                                ],
                                "unknownFutureField": true
                              },
                              "listing": {
                                "method": "psapi",
                                "indexed": "2026-07-13T12:00:00Z",
                                "price": {
                                  "type": "~price",
                                  "amount": 100,
                                  "currency": "divine"
                                }
                              }
                            }
                          ]
                        }
                        """, APPLICATION_JSON));

        TradeFetchResponse response = client.fetch(
                "search-id",
                List.of("listing-1", "listing-2")
        );

        TradeFetchResponse.Result result = response.result().getFirst();
        assertEquals("Mageblood", result.item().name());
        assertEquals("4% increased Strength", result.item().implicitMods().getFirst().description());
        assertEquals("+45 to Dexterity", result.item().explicitMods().getFirst().description());
        assertEquals(
                "stat.explicit.stat_3261801346",
                result.item().explicitMods().getFirst().hash()
        );
        assertEquals(
                "30",
                result.item().explicitMods().getFirst().raw()
                        .get("mods")
                        .get(0)
                        .get("magnitudes")
                        .get(0)
                        .get("min")
                        .asString()
        );
        assertEquals(new BigDecimal("100"), result.listing().price().amount());
        assertEquals("divine", result.listing().price().currency());
        server.verify();
    }

    @Test
    void fetchRejectsMoreThanConfiguredLimit() {
        List<String> listingIds = List.of(
                "id-1", "id-2", "id-3", "id-4", "id-5", "id-6",
                "id-7", "id-8", "id-9", "id-10", "id-11"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> client.fetch("search-id", listingIds)
        );
        server.verify();
    }

    @Test
    void searchMapsRateLimitAndRetryAfter() {
        server.expect(once(), requestTo(
                        "https://www.pathofexile.com/api/trade/search/Mirage"
                ))
                .andRespond(withStatus(TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "7"));

        TradeRateLimitException exception = assertThrows(
                TradeRateLimitException.class,
                () -> client.search(
                        "Mirage",
                        TradeSearchRequest.byName(
                                "Mageblood",
                                TradeSearchRequest.SellerStatus.ONLINE
                        )
                )
        );

        assertEquals(Duration.ofSeconds(7), exception.getRetryAfter());
        server.verify();
    }
}
