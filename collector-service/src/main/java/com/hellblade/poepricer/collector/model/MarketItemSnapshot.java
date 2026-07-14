package com.hellblade.poepricer.collector.model;

import com.hellblade.poepricer.collector.price.ListingPrice;

public record MarketItemSnapshot(
        String itemKey,
        String itemId,
        String league,
        String realm,
        String itemName,
        String typeLine,
        String baseType,
        String rarity,
        Integer itemLevel,
        boolean identified,
        boolean corrupted,
        boolean fractured,
        boolean synthesised,
        boolean elder,
        boolean shaper,
        boolean searing,
        boolean tangled,
        Integer width,
        Integer height,
        Integer x,
        Integer y,
        ListingPrice price,
        String rawItemJson
) {
}
