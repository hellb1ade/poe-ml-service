package com.hellblade.poepricer.collector.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hellblade.poepricer.collector.model.MappedStashPage;
import com.hellblade.poepricer.collector.model.MarketItemSnapshot;
import com.hellblade.poepricer.collector.model.StashObservation;
import com.hellblade.poepricer.collector.price.ListingPrice;

@JdbcTest(properties = "poe.collector.enabled=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcMarketDataRepository.class)
@EnabledIfEnvironmentVariable(named = "POE_COLLECTOR_DB_TEST", matches = "true")
class JdbcMarketDataRepositoryLiveTest {

    @Autowired
    private MarketDataRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void commitsCursorAndJsonSnapshotIdempotently() {
        String suffix = UUID.randomUUID().toString();
        String streamKey = "test-" + suffix;
        String stashId = "stash-" + suffix;
        String itemId = "item-" + suffix;
        String changeId = "change-" + suffix;
        Instant collectedAt = Instant.parse("2026-07-14T10:00:00Z");

        MarketItemSnapshot item = new MarketItemSnapshot(
                itemId,
                itemId,
                "Mirage",
                "pc",
                "Doom Loop",
                "Amethyst Ring",
                "Amethyst Ring",
                "Rare",
                84,
                true,
                false,
                true,
                false,
                true,
                false,
                false,
                false,
                1,
                1,
                3,
                4,
                new ListingPrice(
                        new BigDecimal("2.5"),
                        "divine",
                        "price",
                        "item",
                        "~price 2.5 divine"
                ),
                "{\"id\":\"" + itemId + "\",\"unknownFutureField\":42}"
        );
        MappedStashPage page = new MappedStashPage(
                changeId,
                1,
                1,
                1,
                List.of(new StashObservation(
                        stashId,
                        true,
                        "Mirage",
                        "Sale",
                        "PremiumStash",
                        List.of(item)
                ))
        );

        repository.savePage(streamKey, null, page, collectedAt);
        repository.savePage(streamKey, changeId, page, collectedAt);

        assertEquals(changeId, repository.findCursor(streamKey).orElseThrow());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from collector.market_item_snapshot where item_id = ?",
                Long.class,
                itemId
        ));
        assertEquals("42", jdbcTemplate.queryForObject(
                """
                select raw_item_json ->> 'unknownFutureField'
                  from collector.market_item_snapshot
                 where item_id = ?
                """,
                String.class,
                itemId
        ));
    }
}
