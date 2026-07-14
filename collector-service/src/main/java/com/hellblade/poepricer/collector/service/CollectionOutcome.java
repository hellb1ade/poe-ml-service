package com.hellblade.poepricer.collector.service;

public record CollectionOutcome(
        String nextChangeId,
        boolean endOfStream,
        int upstreamStashCount,
        int targetStashCount,
        int storedItemCount
) {
}
