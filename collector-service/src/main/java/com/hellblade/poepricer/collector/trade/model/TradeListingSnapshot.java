package com.hellblade.poepricer.collector.trade.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeListingSnapshot(
        UUID runId,
        int sampleOrdinal,
        int searchResultRank,
        double priceQuantile,
        String priceBucket,
        String searchId,
        String listingId,
        String itemId,
        String league,
        String itemName,
        String typeLine,
        String baseType,
        String rarity,
        Integer itemLevel,
        Boolean identified,
        Boolean corrupted,
        Boolean fractured,
        Boolean synthesised,
        Boolean mirrored,
        String listingMethod,
        Instant indexedAt,
        String priceType,
        BigDecimal priceAmount,
        String priceCurrency,
        String rawItemJson,
        String rawListingJson,
        String rawResultJson,
        Instant collectedAt
) {
}
