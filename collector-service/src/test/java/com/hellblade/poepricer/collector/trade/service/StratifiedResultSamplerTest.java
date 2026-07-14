package com.hellblade.poepricer.collector.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.hellblade.poepricer.collector.trade.model.SampledListing;

class StratifiedResultSamplerTest {

    private final StratifiedResultSampler sampler = new StratifiedResultSampler();

    @Test
    void samplesAcrossTheWholeOrderedResultSet() {
        List<String> ids = IntStream.range(0, 100)
                .mapToObj(index -> "listing-" + index)
                .toList();

        List<SampledListing> sampled = sampler.sample(ids, 10);

        assertEquals(10, sampled.size());
        assertEquals("listing-5", sampled.getFirst().listingId());
        assertEquals("listing-95", sampled.getLast().listingId());
        assertTrue(sampled.getFirst().priceQuantile() < 0.1);
        assertTrue(sampled.getLast().priceQuantile() > 0.9);
    }

    @Test
    void removesDuplicateListingIdsBeforeSampling() {
        List<SampledListing> sampled = sampler.sample(
                List.of("first", "first", "second"),
                10
        );

        assertEquals(List.of("first", "second"), sampled.stream()
                .map(SampledListing::listingId)
                .toList());
    }
}
