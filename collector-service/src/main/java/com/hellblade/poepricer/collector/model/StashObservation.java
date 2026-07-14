package com.hellblade.poepricer.collector.model;

import java.util.List;

public record StashObservation(
        String stashId,
        boolean publicStash,
        String league,
        String stashName,
        String stashType,
        List<MarketItemSnapshot> items
) {

    public StashObservation {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
