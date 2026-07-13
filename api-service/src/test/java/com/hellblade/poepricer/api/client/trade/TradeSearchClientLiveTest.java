package com.hellblade.poepricer.api.client.trade;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hellblade.poepricer.api.client.trade.dto.TradeFetchResponse;
import com.hellblade.poepricer.api.client.trade.dto.TradeSearchRequest;
import com.hellblade.poepricer.api.client.trade.dto.TradeSearchResponse;

@EnabledIfEnvironmentVariable(named = "POE_TRADE_LIVE_TEST", matches = "true")
@SpringBootTest(properties = {
        "poe.trade.enabled=true",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class TradeSearchClientLiveTest {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TradeSearchClientLiveTest.class);

    @Autowired
    private TradeSearchClient tradeSearchClient;

    @Test
    void searchesAndFetchesLiveListings() {
        String league = requiredEnvironmentVariable("POE_TRADE_LEAGUE");
        String itemName = System.getenv().getOrDefault("POE_TRADE_ITEM_NAME", "Mageblood");
        TradeSearchRequest.SellerStatus sellerStatus = configuredSellerStatus();

        TradeSearchResponse searchResponse = tradeSearchClient.search(
                league,
                TradeSearchRequest.byName(
                        itemName,
                        sellerStatus
                )
        );

        assertNotNull(searchResponse.id());
        assertNotNull(searchResponse.result());
        LOGGER.info(
                "Trade search completed: league={}, status={}, id={}, total={}, returned={}",
                league,
                sellerStatus,
                searchResponse.id(),
                searchResponse.total(),
                searchResponse.result().size()
        );

        List<String> listingIds = searchResponse.result().stream()
                .limit(10)
                .toList();
        if (listingIds.isEmpty()) {
            LOGGER.info("No online listings found for '{}' in league '{}'", itemName, league);
            return;
        }

        TradeFetchResponse fetchResponse = tradeSearchClient.fetch(
                searchResponse.id(),
                listingIds
        );
        assertNotNull(fetchResponse.result());

        fetchResponse.result().forEach(result -> {
            TradeFetchResponse.Item item = result.item();
            TradeFetchResponse.Price price = result.listing() == null
                    ? null
                    : result.listing().price();
            LOGGER.info(
                    "Listing: name='{}', baseType='{}', price={} {}",
                    item == null ? null : item.name(),
                    item == null ? null : item.baseType(),
                    price == null ? null : price.amount(),
                    price == null ? null : price.currency()
            );
        });
    }

    private TradeSearchRequest.SellerStatus configuredSellerStatus() {
        String value = System.getenv().getOrDefault("POE_TRADE_SELLER_STATUS", "ONLINE");
        try {
            return TradeSearchRequest.SellerStatus.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "POE_TRADE_SELLER_STATUS must be ONLINE or ANY",
                    exception
            );
        }
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for the live test");
        }
        return value;
    }
}
