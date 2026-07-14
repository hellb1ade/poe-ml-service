package com.hellblade.poepricer.collector.trade.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hellblade.poepricer.collector.config.TradeDatasetProperties;
import com.hellblade.poepricer.collector.trade.client.dto.TradeFetchResponse;
import com.hellblade.poepricer.collector.trade.client.dto.TradeSearchRequest;
import com.hellblade.poepricer.collector.trade.client.dto.TradeSearchResponse;

class HttpTradeDatasetClientTest {

    private MockRestServiceServer server;
    private HttpTradeDatasetClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://www.pathofexile.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpTradeDatasetClient(builder.build(), properties());
    }

    @Test
    void searchesForAvailablePricedRareBows() {
        server.expect(once(), requestTo(
                        "https://www.pathofexile.com/api/trade/search/Mirage"
                ))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "query": {
                            "status": {"option": "available"},
                            "stats": [{"type": "and", "filters": []}],
                            "filters": {
                              "type_filters": {
                                "filters": {
                                  "rarity": {"option": "rare"},
                                  "category": {"option": "weapon.bow"}
                                }
                              },
                              "trade_filters": {
                                "filters": {
                                  "sale_type": {"option": "any"},
                                  "price": {
                                    "option": "divine",
                                    "min": 1,
                                    "max": 9.9999999999
                                  }
                                }
                              }
                            }
                          },
                          "sort": {"price": "asc"}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "id": "search-1",
                          "result": ["listing-1", "listing-2"],
                          "total": 2000
                        }
                        """, APPLICATION_JSON));

        TradeSearchResponse response = client.search(
                "Mirage",
                TradeSearchRequest.rareItems(
                        "available",
                        "weapon.bow",
                        "divine",
                        new BigDecimal("1"),
                        new BigDecimal("9.9999999999")
                )
        );

        assertEquals("search-1", response.id());
        assertEquals(2000, response.total());
        server.verify();
    }

    @Test
    void searchesWithinAnItemLevelPartition() {
        server.expect(once(), requestTo(
                        "https://www.pathofexile.com/api/trade/search/Mirage"
                ))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "query": {
                            "filters": {
                              "misc_filters": {
                                "filters": {
                                  "ilvl": {"min": 83, "max": 83}
                                }
                              }
                            }
                          }
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "id": "search-ilvl-83",
                          "result": ["listing-1"],
                          "total": 1
                        }
                        """, APPLICATION_JSON));

        TradeSearchResponse response = client.search(
                "Mirage",
                TradeSearchRequest.rareItems(
                        "available",
                        "weapon.bow",
                        "divine",
                        new BigDecimal("10"),
                        new BigDecimal("10.9999999999"),
                        83,
                        83
                )
        );

        assertEquals("search-ilvl-83", response.id());
        server.verify();
    }

    @Test
    void fetchKeepsTheWholeResultTree() {
        server.expect(once(), requestTo(
                        "https://www.pathofexile.com/api/trade/fetch/"
                                + "listing-1,listing-2?query=search-1"
                ))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {
                              "id": "listing-1",
                              "item": {"extended": {"dps": 700}},
                              "listing": {"price": {"amount": 5, "currency": "divine"}}
                            }
                          ]
                        }
                        """, APPLICATION_JSON));

        TradeFetchResponse response = client.fetch(
                "search-1",
                List.of("listing-1", "listing-2")
        );

        assertEquals(
                700,
                response.result().getFirst().get("item").get("extended").get("dps").asInt()
        );
        server.verify();
    }

    private TradeDatasetProperties properties() {
        return new TradeDatasetProperties(
                true,
                URI.create("https://www.pathofexile.com"),
                "poe-pricer-test/0.1 (contact: test@example.com)",
                "Mirage",
                "test-run",
                "weapon.bow",
                "available",
                1000,
                10,
                Duration.ofSeconds(11),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                "divine",
                new BigDecimal("1"),
                new BigDecimal("400")
        );
    }
}
