package com.hellblade.poepricer.collector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hellblade.poepricer.collector.client.dto.PublicStashResponse;
import com.hellblade.poepricer.collector.config.CollectorProperties;
import com.hellblade.poepricer.collector.model.MappedStashPage;
import com.hellblade.poepricer.collector.model.MarketItemSnapshot;
import com.hellblade.poepricer.collector.model.StashObservation;
import com.hellblade.poepricer.collector.price.PriceParser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PublicStashPageMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PublicStashPageMapper mapper = new PublicStashPageMapper(
            new CollectorProperties(
                    true,
                    "pc",
                    "Mirage",
                    "test-stream",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(5),
                    Duration.ofMinutes(1),
                    true
            ),
            new PriceParser()
    );

    @Test
    void keepsAllRawItemFieldsAndStoresOnlyPricedRareItemsFromTargetLeague() {
        JsonNode targetStash = objectMapper.readTree("""
                {
                  "id": "stash-1",
                  "public": true,
                  "league": "Mirage",
                  "stash": "~price 10 chaos",
                  "stashType": "PremiumStash",
                  "items": [
                    {
                      "id": "item-1",
                      "league": "Mirage",
                      "name": "Doom Loop",
                      "typeLine": "Amethyst Ring",
                      "baseType": "Amethyst Ring",
                      "rarity": "Rare",
                      "ilvl": 84,
                      "identified": true,
                      "fractured": true,
                      "influences": {"elder": true},
                      "w": 1,
                      "h": 1,
                      "x": 3,
                      "y": 4,
                      "note": "~price 2 divine",
                      "explicitMods": ["+90 to maximum Life"],
                      "sockets": [{"group": 0, "sColour": "R"}],
                      "unknownFutureField": {"value": 42}
                    },
                    {
                      "id": "item-2",
                      "rarity": "Unique",
                      "identified": true
                    }
                  ]
                }
                """);
        JsonNode otherLeague = objectMapper.readTree("""
                {"id":"stash-2","public":true,"league":"Standard","items":[]}
                """);
        JsonNode privateStash = objectMapper.readTree("""
                {"id":"stash-3","public":false}
                """);

        MappedStashPage page = mapper.map(new PublicStashResponse(
                "next-1",
                List.of(targetStash, otherLeague, privateStash)
        ));

        assertEquals(3, page.upstreamStashCount());
        assertEquals(1, page.targetStashCount());
        assertEquals(1, page.eligibleItemCount());
        assertEquals(2, page.stashes().size());

        StashObservation target = page.stashes().getFirst();
        MarketItemSnapshot item = target.items().getFirst();
        assertEquals("item-1", item.itemKey());
        assertEquals("Amethyst Ring", item.baseType());
        assertEquals(84, item.itemLevel());
        assertTrue(item.fractured());
        assertTrue(item.elder());
        assertEquals("pc", item.realm());
        assertEquals("divine", item.price().currency());
        assertEquals("item", item.price().source());
        assertTrue(item.rawItemJson().contains("unknownFutureField"));
        assertTrue(item.rawItemJson().contains("explicitMods"));
        assertTrue(item.rawItemJson().contains("sockets"));

        assertFalse(page.stashes().get(1).publicStash());
    }

    @Test
    void usesStashWidePriceWhenItemHasNoPriceNote() {
        JsonNode stash = objectMapper.readTree("""
                {
                  "id": "stash-1",
                  "public": true,
                  "league": "Mirage",
                  "stash": "~price 20 chaos",
                  "items": [
                    {"id":"item-1","rarity":"Rare","identified":true}
                  ]
                }
                """);

        MarketItemSnapshot item = mapper.map(
                new PublicStashResponse("next-1", List.of(stash))
        ).stashes().getFirst().items().getFirst();

        assertEquals("chaos", item.price().currency());
        assertEquals("stash", item.price().source());
    }
}
