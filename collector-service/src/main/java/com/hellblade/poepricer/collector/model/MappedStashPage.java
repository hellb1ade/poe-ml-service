package com.hellblade.poepricer.collector.model;

import java.util.List;

public record MappedStashPage(
        String nextChangeId,
        int upstreamStashCount,
        int targetStashCount,
        int eligibleItemCount,
        List<StashObservation> stashes
) {

    public MappedStashPage {
        stashes = List.copyOf(stashes);
    }
}
