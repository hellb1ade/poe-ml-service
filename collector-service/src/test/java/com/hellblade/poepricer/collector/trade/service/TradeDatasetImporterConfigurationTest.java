package com.hellblade.poepricer.collector.trade.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hellblade.poepricer.collector.config.TradeDatasetProperties;
import com.hellblade.poepricer.collector.trade.model.TradePriceBucket;

class TradeDatasetImporterConfigurationTest {

    @Test
    void supportsThreeThousandItemsAndCapsEveryBucketAtConfiguredMaximum() {
        TradeDatasetProperties properties = properties(
                3000,
                new BigDecimal("1"),
                new BigDecimal("400")
        );

        assertDoesNotThrow(properties::validate);

        TradeDatasetImporter importer = new TradeDatasetImporter(
                null,
                null,
                null,
                null,
                properties,
                Clock.systemUTC()
        );
        List<TradePriceBucket> buckets = importer.priceBuckets();

        assertEquals(60, buckets.size());
        assertEquals(0, new BigDecimal("400").compareTo(buckets.getLast().maximum()));
        assertTrue(buckets.stream().allMatch(bucket ->
                bucket.maximum().compareTo(new BigDecimal("400")) <= 0
        ));
    }

    @Test
    void rejectsMaximumPriceThatDoesNotExceedMinimum() {
        TradeDatasetProperties properties = properties(
                3000,
                new BigDecimal("1"),
                new BigDecimal("1")
        );

        assertThrows(IllegalStateException.class, properties::validate);
    }

    private TradeDatasetProperties properties(
            int targetSize,
            BigDecimal minimumPrice,
            BigDecimal maximumPrice
    ) {
        return new TradeDatasetProperties(
                true,
                URI.create("https://www.pathofexile.com"),
                "poe-pricer-test/0.1 (contact: test@example.com)",
                "Mirage",
                "test-run",
                "weapon.bow",
                "available",
                targetSize,
                10,
                Duration.ofSeconds(11),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                "divine",
                minimumPrice,
                maximumPrice
        );
    }
}
