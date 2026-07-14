package com.hellblade.poepricer.collector.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hellblade.poepricer.collector.trade.model.SampledListing;
import com.hellblade.poepricer.collector.trade.model.TradeListingSnapshot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class TradeListingMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TradeListingMapper mapper = new TradeListingMapper();

    @Test
    void preservesRawItemListingAndUnknownFields() throws Exception {
        JsonNode result = objectMapper.readTree("""
                {
                  "id": "listing-1",
                  "item": {
                    "id": "item-1",
                    "league": "Mirage",
                    "name": "Doom Branch",
                    "typeLine": "Spine Bow",
                    "baseType": "Spine Bow",
                    "rarity": "Rare",
                    "ilvl": 86,
                    "identified": true,
                    "fractured": true,
                    "explicitMods": [
                      {"description": "+2 to Level of Socketed Bow Gems"}
                    ],
                    "extended": {"dps": 712.5},
                    "futureField": {"keep": true}
                  },
                  "listing": {
                    "method": "psapi",
                    "indexed": "2026-07-14T10:00:00Z",
                    "price": {
                      "type": "~price",
                      "amount": 12.5,
                      "currency": "divine"
                    },
                    "whisper_token": "raw-value"
                  }
                }
                """);

        TradeListingSnapshot snapshot = mapper.map(
                UUID.randomUUID(),
                "search-1",
                "Mirage",
                new SampledListing(1, 50, 0.5, "divine-10-under-25", "listing-1"),
                result,
                Instant.parse("2026-07-14T11:00:00Z")
        ).orElseThrow();

        assertEquals("Spine Bow", snapshot.baseType());
        assertEquals(new BigDecimal("12.5"), snapshot.priceAmount());
        assertEquals("divine", snapshot.priceCurrency());
        assertTrue(snapshot.rawItemJson().contains("futureField"));
        assertTrue(snapshot.rawItemJson().contains("extended"));
        assertTrue(snapshot.rawListingJson().contains("whisper_token"));
    }
}
