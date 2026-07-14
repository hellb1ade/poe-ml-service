package com.hellblade.poepricer.collector.trade.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.hellblade.poepricer.collector.config.TradeDatasetProperties;
import com.hellblade.poepricer.collector.trade.client.TradeDatasetClient;
import com.hellblade.poepricer.collector.trade.client.TradeDatasetRateLimitException;
import com.hellblade.poepricer.collector.trade.client.dto.TradeFetchResponse;
import com.hellblade.poepricer.collector.trade.client.dto.TradeSearchRequest;
import com.hellblade.poepricer.collector.trade.client.dto.TradeSearchResponse;
import com.hellblade.poepricer.collector.trade.model.ExistingTradeDatasetRun;
import com.hellblade.poepricer.collector.trade.model.SampledListing;
import com.hellblade.poepricer.collector.trade.model.TradeListingSnapshot;
import com.hellblade.poepricer.collector.trade.model.TradePriceBucket;
import com.hellblade.poepricer.collector.trade.repository.TradeDatasetRepository;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(prefix = "poe.trade-dataset", name = "enabled", havingValue = "true")
public class TradeDatasetImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradeDatasetImporter.class);
    private static final BigDecimal PRICE_BOUNDARY_EPSILON = new BigDecimal("0.0000000001");
    private static final int SEARCH_PARTITIONS_PER_QUOTA_GROUP = 15;
    private static final int CANDIDATE_BUFFER = 100;

    private final TradeDatasetClient client;
    private final TradeDatasetRepository repository;
    private final StratifiedResultSampler sampler;
    private final TradeListingMapper mapper;
    private final TradeDatasetProperties properties;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile Thread worker;

    @Autowired
    public TradeDatasetImporter(
            TradeDatasetClient client,
            TradeDatasetRepository repository,
            StratifiedResultSampler sampler,
            TradeListingMapper mapper,
            TradeDatasetProperties properties
    ) {
        this(client, repository, sampler, mapper, properties, Clock.systemUTC());
    }

    TradeDatasetImporter(
            TradeDatasetClient client,
            TradeDatasetRepository repository,
            StratifiedResultSampler sampler,
            TradeListingMapper mapper,
            TradeDatasetProperties properties,
            Clock clock
    ) {
        this.client = client;
        this.repository = repository;
        this.sampler = sampler;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        properties.validate();
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = Thread.ofVirtual()
                .name("trade-dataset-importer")
                .start(this::importDataset);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        Thread currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.interrupt();
            if (currentWorker != Thread.currentThread()) {
                try {
                    currentWorker.join(Duration.ofSeconds(5));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void importDataset() {
        UUID runId = null;
        try {
            ExistingTradeDatasetRun existing = repository.findRun(properties.runKey())
                    .orElse(null);
            if (existing != null) {
                LOGGER.info(
                        "Trade dataset run '{}' already exists: status={}, storedItems={}; skipping",
                        properties.runKey(),
                        existing.status(),
                        existing.storedItems()
                );
                return;
            }

            Set<String> existingListingIds = repository.findAllListingIds();
            Set<String> knownItemIds = new HashSet<>(repository.findAllItemIds());
            runId = repository.startRun(properties, clock.instant());
            List<BucketSearch> searches = searchPriceBuckets(existingListingIds);
            List<BucketPlan> plans = createPlans(runId, searches, properties.targetSize());
            int selected = plans.stream().mapToInt(plan -> plan.sampled().size()).sum();
            if (selected < properties.targetSize()) {
                throw new IllegalStateException(
                        "Trade searches returned only " + selected + " unique listing ids; "
                                + properties.targetSize() + " are required"
                );
            }

            int stored = fetchAndStore(runId, plans, knownItemIds);
            if (stored < properties.targetSize()) {
                throw new IllegalStateException(
                        "Only " + stored + " of " + properties.targetSize()
                                + " requested unique items could be stored"
                );
            }
            repository.finishRun(runId, "COMPLETED", null, clock.instant());
            LOGGER.info(
                    "Trade dataset import completed: runKey={}, selected={}, stored={}",
                    properties.runKey(),
                    selected,
                    stored
            );
        } catch (TradeDatasetRateLimitException exception) {
            String message = "Stopped on HTTP 429; Retry-After="
                    + exception.retryAfter().toSeconds() + "s";
            finishIfStarted(runId, "RATE_LIMITED", message);
            LOGGER.warn("{}; no automatic retry will be attempted", message);
        } catch (InterruptedException exception) {
            finishIfStarted(runId, "FAILED", "Importer was interrupted");
            Thread.currentThread().interrupt();
            LOGGER.warn("Trade dataset import interrupted");
        } catch (RuntimeException exception) {
            finishIfStarted(runId, "FAILED", conciseMessage(exception));
            LOGGER.error("Trade dataset import failed", exception);
        } finally {
            running.set(false);
        }
    }

    private List<BucketSearch> searchPriceBuckets(Set<String> existingListingIds)
            throws InterruptedException {
        Set<String> seenListingIds = new HashSet<>(existingListingIds);
        List<BucketSearch> searches = new ArrayList<>();
        for (TradePriceBucket bucket : priceBuckets()) {
            searches.add(executeSearch(bucket, seenListingIds));
        }

        supplementTruncatedSearches(searches, seenListingIds);
        LOGGER.info(
                "Trade search phase completed: searches={}, uniqueCandidates={}, target={}",
                searches.size(),
                candidateCount(searches),
                properties.targetSize()
        );
        return List.copyOf(searches);
    }

    private BucketSearch executeSearch(
            TradePriceBucket bucket,
            Set<String> seenListingIds
    ) throws InterruptedException {
        sleep(properties.searchRequestDelay());
        TradeSearchRequest request = TradeSearchRequest.rareItems(
                properties.availability(),
                properties.category(),
                properties.priceCurrency(),
                bucket.minimum(),
                bucket.maximum(),
                bucket.minimumItemLevel(),
                bucket.maximumItemLevel()
        );
        TradeSearchResponse response = client.search(properties.league(), request);
        requireSearchResponse(response);
        int totalMatches = response.total() == null
                ? response.result().size()
                : response.total();
        List<String> newListingIds = response.result().stream()
                .filter(seenListingIds::add)
                .toList();

        LOGGER.info(
                "Trade price bucket searched: bucket={}, currency={}, min={}, max={}, "
                        + "ilvlMin={}, ilvlMax={}, total={}, returnedIds={}, newIds={}",
                bucket.key(),
                properties.priceCurrency(),
                bucket.minimum(),
                bucket.maximum(),
                bucket.minimumItemLevel(),
                bucket.maximumItemLevel(),
                totalMatches,
                response.result().size(),
                newListingIds.size()
        );
        return new BucketSearch(bucket, response, totalMatches, newListingIds);
    }

    private void supplementTruncatedSearches(
            List<BucketSearch> searches,
            Set<String> seenListingIds
    ) throws InterruptedException {
        List<BucketSearch> baseSearches = List.copyOf(searches);
        Set<String> expandedBuckets = new HashSet<>();
        List<String> quotaGroups = baseSearches.stream()
                .map(search -> search.bucket().quotaGroup())
                .distinct()
                .toList();
        int groupTarget = properties.targetSize() / quotaGroups.size();

        for (String quotaGroup : quotaGroups) {
            for (BucketSearch baseSearch : baseSearches) {
                if (groupCandidateCount(searches, quotaGroup) >= groupTarget) {
                    break;
                }
                if (!baseSearch.bucket().quotaGroup().equals(quotaGroup)
                        || !isTruncated(baseSearch)
                        || !expandedBuckets.add(baseSearch.bucket().key())) {
                    continue;
                }
                addItemLevelSearches(searches, baseSearch.bucket(), seenListingIds);
            }
        }

        int desiredCandidates = properties.targetSize() + CANDIDATE_BUFFER;
        while (candidateCount(searches) < desiredCandidates) {
            BucketSearch nextSearch = null;
            int smallestGroup = Integer.MAX_VALUE;
            for (BucketSearch baseSearch : baseSearches) {
                if (!isTruncated(baseSearch)
                        || expandedBuckets.contains(baseSearch.bucket().key())) {
                    continue;
                }
                int groupSize = groupCandidateCount(
                        searches,
                        baseSearch.bucket().quotaGroup()
                );
                if (groupSize < smallestGroup) {
                    smallestGroup = groupSize;
                    nextSearch = baseSearch;
                }
            }
            if (nextSearch == null) {
                break;
            }
            expandedBuckets.add(nextSearch.bucket().key());
            addItemLevelSearches(searches, nextSearch.bucket(), seenListingIds);
        }
    }

    private void addItemLevelSearches(
            List<BucketSearch> searches,
            TradePriceBucket baseBucket,
            Set<String> seenListingIds
    ) throws InterruptedException {
        for (TradePriceBucket partition : itemLevelPartitions(baseBucket)) {
            searches.add(executeSearch(partition, seenListingIds));
        }
    }

    private List<TradePriceBucket> itemLevelPartitions(TradePriceBucket bucket) {
        return List.of(
                itemLevelBucket(bucket, "i00-82", null, 82),
                itemLevelBucket(bucket, "i83", 83, 83),
                itemLevelBucket(bucket, "i84", 84, 84),
                itemLevelBucket(bucket, "i85", 85, 85),
                itemLevelBucket(bucket, "i86p", 86, null)
        );
    }

    private TradePriceBucket itemLevelBucket(
            TradePriceBucket bucket,
            String suffix,
            Integer minimumItemLevel,
            Integer maximumItemLevel
    ) {
        return new TradePriceBucket(
                bucket.key() + "-" + suffix,
                bucket.quotaGroup(),
                bucket.minimum(),
                bucket.maximum(),
                minimumItemLevel,
                maximumItemLevel
        );
    }

    private boolean isTruncated(BucketSearch search) {
        return search.totalMatches() > search.response().result().size();
    }

    private int candidateCount(List<BucketSearch> searches) {
        return searches.stream().mapToInt(search -> search.newListingIds().size()).sum();
    }

    private int groupCandidateCount(List<BucketSearch> searches, String quotaGroup) {
        return searches.stream()
                .filter(search -> search.bucket().quotaGroup().equals(quotaGroup))
                .mapToInt(search -> search.newListingIds().size())
                .sum();
    }

    private List<BucketPlan> createPlans(
            UUID runId,
            List<BucketSearch> searches,
            int candidateTarget
    ) {
        int[] quotas = allocateQuotas(searches, candidateTarget);
        List<BucketPlan> plans = new ArrayList<>();
        int ordinalOffset = 0;

        for (int index = 0; index < searches.size(); index++) {
            BucketSearch search = searches.get(index);
            List<SampledListing> sampled = sampler.sample(
                    search.newListingIds(),
                    quotas[index],
                    search.bucket().key(),
                    ordinalOffset
            );
            ordinalOffset += sampled.size();
            repository.recordSearch(
                    runId,
                    search.bucket(),
                    search.response().id(),
                    properties.priceCurrency(),
                    search.totalMatches(),
                    sampled.size(),
                    clock.instant()
            );
            plans.add(new BucketPlan(search.bucket(), search.response().id(), sampled));

            LOGGER.info(
                    "Trade price bucket planned: bucket={}, availableIds={}, selected={}",
                    search.bucket().key(),
                    search.newListingIds().size(),
                    sampled.size()
            );
        }
        return List.copyOf(plans);
    }

    private int[] allocateQuotas(List<BucketSearch> searches, int targetSize) {
        int[] quotas = new int[searches.size()];
        Map<String, List<Integer>> indexesByGroup = new LinkedHashMap<>();
        for (int index = 0; index < searches.size(); index++) {
            indexesByGroup.computeIfAbsent(
                    searches.get(index).bucket().quotaGroup(),
                    ignored -> new ArrayList<>()
            ).add(index);
        }

        int baseGroupQuota = targetSize / indexesByGroup.size();
        int groupRemainder = targetSize % indexesByGroup.size();
        int groupIndex = 0;
        int allocated = 0;
        for (List<Integer> indexes : indexesByGroup.values()) {
            int desired = baseGroupQuota + (groupIndex < groupRemainder ? 1 : 0);
            allocated += allocateAcross(searches, quotas, indexes, desired);
            groupIndex++;
        }

        if (allocated < targetSize) {
            List<Integer> allIndexes = new ArrayList<>();
            for (int index = 0; index < searches.size(); index++) {
                allIndexes.add(index);
            }
            allocateAcross(searches, quotas, allIndexes, targetSize - allocated);
        }
        return quotas;
    }

    private int allocateAcross(
            List<BucketSearch> searches,
            int[] quotas,
            List<Integer> indexes,
            int desired
    ) {
        int allocated = 0;
        while (allocated < desired) {
            boolean progressed = false;
            for (int index : indexes) {
                if (allocated >= desired) {
                    break;
                }
                int capacity = searches.get(index).newListingIds().size();
                if (quotas[index] < capacity) {
                    quotas[index]++;
                    allocated++;
                    progressed = true;
                }
            }
            if (!progressed) {
                break;
            }
        }
        return allocated;
    }

    private int fetchAndStore(
            UUID runId,
            List<BucketPlan> plans,
            Set<String> knownItemIds
    ) throws InterruptedException {
        int stored = 0;
        outer:
        for (BucketPlan plan : plans) {
            int batchCount = (plan.sampled().size() + properties.fetchSize() - 1)
                    / properties.fetchSize();
            int batchNumber = 0;

            for (int offset = 0; offset < plan.sampled().size() && running.get();
                    offset += properties.fetchSize()) {
                if (stored >= properties.targetSize()) {
                    break outer;
                }
                int end = Math.min(offset + properties.fetchSize(), plan.sampled().size());
                List<SampledListing> batch = plan.sampled().subList(offset, end);
                sleep(properties.fetchRequestDelay());

                TradeFetchResponse response = client.fetch(
                        plan.searchId(),
                        batch.stream().map(SampledListing::listingId).toList()
                );
                List<TradeListingSnapshot> snapshots = mapBatch(
                        runId,
                        plan.searchId(),
                        batch,
                        response.result(),
                        clock.instant(),
                        knownItemIds
                );
                int remaining = properties.targetSize() - stored;
                if (snapshots.size() > remaining) {
                    snapshots = snapshots.subList(0, remaining);
                }
                stored += repository.saveBatch(runId, snapshots);
                batchNumber++;

                LOGGER.info(
                        "Trade dataset batch committed: bucket={}, batch={}/{}, requested={}, "
                                + "received={}, mapped={}, storedTotal={}",
                        plan.bucket().key(),
                        batchNumber,
                        batchCount,
                        batch.size(),
                        response.result().size(),
                        snapshots.size(),
                        stored
                );
            }
        }
        return stored;
    }

    private List<TradeListingSnapshot> mapBatch(
            UUID runId,
            String searchId,
            List<SampledListing> sampled,
            List<JsonNode> results,
            Instant collectedAt,
            Set<String> knownItemIds
    ) {
        Map<String, SampledListing> sampleByListingId = new HashMap<>();
        sampled.forEach(candidate -> sampleByListingId.put(candidate.listingId(), candidate));

        List<TradeListingSnapshot> snapshots = new ArrayList<>();
        for (JsonNode result : results) {
            String listingId = text(result, "id");
            SampledListing candidate = sampleByListingId.get(listingId);
            if (candidate == null) {
                LOGGER.warn("Trade fetch returned an unexpected listing id: {}", listingId);
                continue;
            }
            mapper.map(
                    runId,
                    searchId,
                    properties.league(),
                    candidate,
                    result,
                    collectedAt
            ).ifPresent(snapshot -> {
                String itemId = snapshot.itemId();
                if (itemId == null || itemId.isBlank()) {
                    LOGGER.warn("Skipping Trade listing without an item id: {}", listingId);
                    return;
                }
                if (!knownItemIds.add(itemId)) {
                    LOGGER.debug("Skipping already collected item id: {}", itemId);
                    return;
                }
                snapshots.add(snapshot);
            });
        }
        return snapshots;
    }

    private void requireSearchResponse(TradeSearchResponse response) {
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new IllegalStateException("Trade search response did not contain an id");
        }
    }

    List<TradePriceBucket> priceBuckets() {
        List<PriceRangeGroup> groups = List.of(
                new PriceRangeGroup("price-1-10", new BigDecimal("1"), new BigDecimal("10"), false),
                new PriceRangeGroup("price-10-25", new BigDecimal("10"), new BigDecimal("25"), false),
                new PriceRangeGroup("price-25-100", new BigDecimal("25"), new BigDecimal("100"), false),
                new PriceRangeGroup("price-100-400", new BigDecimal("100"), new BigDecimal("400"), true)
        );

        List<TradePriceBucket> buckets = new ArrayList<>();
        for (PriceRangeGroup group : groups) {
            BigDecimal width = group.maximum()
                    .subtract(group.minimum())
                    .divide(BigDecimal.valueOf(SEARCH_PARTITIONS_PER_QUOTA_GROUP));
            for (int index = 0; index < SEARCH_PARTITIONS_PER_QUOTA_GROUP; index++) {
                BigDecimal lowerBoundary = group.minimum().add(
                        width.multiply(BigDecimal.valueOf(index))
                );
                BigDecimal upperBoundary = group.minimum().add(
                        width.multiply(BigDecimal.valueOf(index + 1L))
                );
                BigDecimal minimum = lowerBoundary.max(properties.minimumPrice());
                BigDecimal maximum = upperBoundary;
                boolean inclusiveFinalBoundary = group.inclusiveMaximum()
                        && index == SEARCH_PARTITIONS_PER_QUOTA_GROUP - 1;
                if (!inclusiveFinalBoundary) {
                    maximum = maximum.subtract(PRICE_BOUNDARY_EPSILON);
                }
                maximum = maximum.min(properties.maximumPrice());

                if (minimum.compareTo(maximum) <= 0) {
                    buckets.add(new TradePriceBucket(
                            group.key() + "-" + String.format("%02d", index + 1),
                            group.key(),
                            minimum,
                            maximum
                    ));
                }
            }
        }
        return List.copyOf(buckets);
    }

    private void finishIfStarted(UUID runId, String status, String message) {
        if (runId == null) {
            return;
        }
        try {
            repository.finishRun(runId, status, message, clock.instant());
        } catch (RuntimeException finishException) {
            LOGGER.error("Could not update Trade dataset run status", finishException);
        }
    }

    private void sleep(Duration duration) throws InterruptedException {
        if (!running.get()) {
            return;
        }
        Thread.sleep(duration);
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isString()
                ? null
                : value.asString();
    }

    private String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record BucketSearch(
            TradePriceBucket bucket,
            TradeSearchResponse response,
            int totalMatches,
            List<String> newListingIds
    ) {
    }

    private record BucketPlan(
            TradePriceBucket bucket,
            String searchId,
            List<SampledListing> sampled
    ) {
    }

    private record PriceRangeGroup(
            String key,
            BigDecimal minimum,
            BigDecimal maximum,
            boolean inclusiveMaximum
    ) {
    }
}
