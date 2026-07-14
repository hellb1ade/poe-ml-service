package com.hellblade.poepricer.collector.repository;

import java.time.Instant;
import java.util.Optional;

import com.hellblade.poepricer.collector.model.MappedStashPage;

public interface MarketDataRepository {

    Optional<String> findCursor(String streamKey);

    void savePage(
            String streamKey,
            String expectedCursor,
            MappedStashPage page,
            Instant collectedAt
    );
}
