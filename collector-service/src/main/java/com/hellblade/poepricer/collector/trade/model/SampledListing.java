package com.hellblade.poepricer.collector.trade.model;

public record SampledListing(
        int ordinal,
        int searchResultRank,
        double priceQuantile,
        String priceBucket,
        String listingId
) {
}
