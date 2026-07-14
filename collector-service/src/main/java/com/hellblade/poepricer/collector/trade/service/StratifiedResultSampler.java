package com.hellblade.poepricer.collector.trade.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.hellblade.poepricer.collector.trade.model.SampledListing;

@Component
public class StratifiedResultSampler {

    public List<SampledListing> sample(List<String> orderedListingIds, int targetSize) {
        return sample(orderedListingIds, targetSize, "unbucketed", 0);
    }

    public List<SampledListing> sample(
            List<String> orderedListingIds,
            int targetSize,
            String priceBucket,
            int ordinalOffset
    ) {
        if (targetSize == 0 || orderedListingIds == null || orderedListingIds.isEmpty()) {
            return List.of();
        }
        if (targetSize < 0) {
            throw new IllegalArgumentException("Target size must not be negative");
        }

        List<String> uniqueIds = new ArrayList<>(new LinkedHashSet<>(orderedListingIds));
        int resultSize = uniqueIds.size();
        int sampleSize = Math.min(targetSize, resultSize);
        List<SampledListing> sampled = new ArrayList<>(sampleSize);

        for (int index = 0; index < sampleSize; index++) {
            int rank = (int) Math.floor((index + 0.5d) * resultSize / sampleSize);
            rank = Math.min(rank, resultSize - 1);
            double quantile = (rank + 0.5d) / resultSize;
            sampled.add(new SampledListing(
                    ordinalOffset + index + 1,
                    rank,
                    quantile,
                    priceBucket,
                    uniqueIds.get(rank)
            ));
        }
        return List.copyOf(sampled);
    }
}
