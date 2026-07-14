package com.hellblade.poepricer.collector.service;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.hellblade.poepricer.collector.client.PublicStashClient;
import com.hellblade.poepricer.collector.client.dto.PublicStashResponse;
import com.hellblade.poepricer.collector.config.CollectorProperties;
import com.hellblade.poepricer.collector.model.MappedStashPage;
import com.hellblade.poepricer.collector.repository.MarketDataRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@ConditionalOnProperty(prefix = "poe.collector", name = "enabled", havingValue = "true")
public class PublicStashCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicStashCollector.class);

    private final PublicStashClient client;
    private final PublicStashPageMapper mapper;
    private final MarketDataRepository repository;
    private final CollectorProperties properties;
    private final Clock clock;
    private final Counter pagesCounter;
    private final Counter upstreamStashesCounter;
    private final Counter targetStashesCounter;
    private final Counter storedItemsCounter;

    public PublicStashCollector(
            PublicStashClient client,
            PublicStashPageMapper mapper,
            MarketDataRepository repository,
            CollectorProperties properties,
            MeterRegistry meterRegistry
    ) {
        this(client, mapper, repository, properties, meterRegistry, Clock.systemUTC());
    }

    PublicStashCollector(
            PublicStashClient client,
            PublicStashPageMapper mapper,
            MarketDataRepository repository,
            CollectorProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.client = client;
        this.mapper = mapper;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.pagesCounter = meterRegistry.counter("poe.collector.pages");
        this.upstreamStashesCounter = meterRegistry.counter("poe.collector.stashes.upstream");
        this.targetStashesCounter = meterRegistry.counter("poe.collector.stashes.target");
        this.storedItemsCounter = meterRegistry.counter("poe.collector.items.stored");
    }

    public Optional<String> currentCursor() {
        return repository.findCursor(properties.streamKey());
    }

    public CollectionOutcome collectNextPage(String cursor) {
        PublicStashResponse response = client.getNextPage(cursor);
        MappedStashPage page = mapper.map(response);
        repository.savePage(properties.streamKey(), cursor, page, clock.instant());

        pagesCounter.increment();
        upstreamStashesCounter.increment(page.upstreamStashCount());
        targetStashesCounter.increment(page.targetStashCount());
        storedItemsCounter.increment(page.eligibleItemCount());

        LOGGER.info(
                "Public Stash page committed: nextChangeId={}, upstreamStashes={}, "
                        + "targetStashes={}, storedItems={}",
                page.nextChangeId(),
                page.upstreamStashCount(),
                page.targetStashCount(),
                page.eligibleItemCount()
        );

        return new CollectionOutcome(
                page.nextChangeId(),
                page.upstreamStashCount() == 0,
                page.upstreamStashCount(),
                page.targetStashCount(),
                page.eligibleItemCount()
        );
    }
}
