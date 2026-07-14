package com.hellblade.poepricer.collector.trade.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hellblade.poepricer.collector.config.TradeDatasetProperties;
import com.hellblade.poepricer.collector.trade.model.ExistingTradeDatasetRun;
import com.hellblade.poepricer.collector.trade.model.TradeListingSnapshot;
import com.hellblade.poepricer.collector.trade.model.TradePriceBucket;

@Repository
@ConditionalOnProperty(prefix = "poe.trade-dataset", name = "enabled", havingValue = "true")
public class TradeDatasetRepository {

    private static final String INSERT_LISTING_SQL = """
            insert into collector.trade_listing_snapshot (
                run_id, sample_ordinal, search_result_rank, price_quantile, price_bucket,
                search_id, listing_id, item_id, league, item_name, type_line,
                base_type, rarity, item_level, identified, corrupted, fractured,
                synthesised, mirrored, listing_method, indexed_at, price_type,
                price_amount, price_currency, raw_item_json, raw_listing_json,
                raw_result_json, collected_at
            ) values (
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, cast(? as jsonb), cast(? as jsonb),
                cast(? as jsonb), ?
            )
            on conflict (listing_id) do nothing
            """;

    private final JdbcTemplate jdbcTemplate;

    public TradeDatasetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ExistingTradeDatasetRun> findRun(String runKey) {
        List<ExistingTradeDatasetRun> runs = jdbcTemplate.query(
                """
                select id, status, stored_items
                  from collector.trade_dataset_run
                 where run_key = ?
                """,
                (resultSet, rowNumber) -> new ExistingTradeDatasetRun(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("status"),
                        resultSet.getInt("stored_items")
                ),
                runKey
        );
        return runs.stream().findFirst();
    }

    public Set<String> findAllListingIds() {
        return Set.copyOf(jdbcTemplate.queryForList(
                "select listing_id from collector.trade_listing_snapshot",
                String.class
        ));
    }

    public Set<String> findAllItemIds() {
        return Set.copyOf(jdbcTemplate.queryForList(
                """
                select item_id
                  from collector.trade_listing_snapshot
                 where item_id is not null
                """,
                String.class
        ));
    }

    public UUID startRun(TradeDatasetProperties properties, Instant startedAt) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into collector.trade_dataset_run (
                    id, run_key, league, item_category, availability,
                    requested_items, status, started_at
                ) values (?, ?, ?, ?, ?, ?, 'RUNNING', ?)
                """,
                runId,
                properties.runKey(),
                properties.league(),
                properties.category(),
                properties.availability(),
                properties.targetSize(),
                Timestamp.from(startedAt)
        );
        return runId;
    }

    @Transactional
    public void recordSearch(
            UUID runId,
            TradePriceBucket bucket,
            String searchId,
            String priceCurrency,
            int totalMatches,
            int selectedItems,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                insert into collector.trade_dataset_search (
                    run_id, price_bucket, search_id, price_currency,
                    minimum_price, maximum_price, minimum_item_level,
                    maximum_item_level, total_matches, selected_items, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                runId,
                bucket.key(),
                searchId,
                priceCurrency,
                bucket.minimum(),
                bucket.maximum(),
                bucket.minimumItemLevel(),
                bucket.maximumItemLevel(),
                totalMatches,
                selectedItems,
                Timestamp.from(createdAt)
        );
        jdbcTemplate.update(
                """
                update collector.trade_dataset_run
                   set search_id = coalesce(search_id, ?),
                       total_matches = coalesce(total_matches, 0) + ?,
                       selected_items = coalesce(selected_items, 0) + ?
                 where id = ?
                """,
                searchId,
                totalMatches,
                selectedItems,
                runId
        );
    }

    @Transactional
    public int saveBatch(UUID runId, List<TradeListingSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return 0;
        }

        int[][] results = jdbcTemplate.batchUpdate(
                INSERT_LISTING_SQL,
                snapshots,
                snapshots.size(),
                this::bindListing
        );
        int inserted = Arrays.stream(results)
                .flatMapToInt(Arrays::stream)
                .map(result -> result == Statement.SUCCESS_NO_INFO ? 1 : Math.max(0, result))
                .sum();

        if (inserted > 0) {
            jdbcTemplate.update(
                    """
                    update collector.trade_dataset_run
                       set stored_items = stored_items + ?
                     where id = ?
                    """,
                    inserted,
                    runId
            );
        }
        return inserted;
    }

    public void finishRun(
            UUID runId,
            String status,
            String errorMessage,
            Instant finishedAt
    ) {
        jdbcTemplate.update(
                """
                update collector.trade_dataset_run
                   set status = ?, error_message = ?, finished_at = ?
                 where id = ?
                """,
                status,
                errorMessage,
                Timestamp.from(finishedAt),
                runId
        );
    }

    private void bindListing(
            PreparedStatement statement,
            TradeListingSnapshot snapshot
    ) throws SQLException {
        int index = 1;
        statement.setObject(index++, snapshot.runId());
        statement.setInt(index++, snapshot.sampleOrdinal());
        statement.setInt(index++, snapshot.searchResultRank());
        statement.setDouble(index++, snapshot.priceQuantile());
        statement.setString(index++, snapshot.priceBucket());
        statement.setString(index++, snapshot.searchId());
        statement.setString(index++, snapshot.listingId());
        setNullableString(statement, index++, snapshot.itemId());
        statement.setString(index++, snapshot.league());
        setNullableString(statement, index++, snapshot.itemName());
        setNullableString(statement, index++, snapshot.typeLine());
        setNullableString(statement, index++, snapshot.baseType());
        setNullableString(statement, index++, snapshot.rarity());
        setNullableInteger(statement, index++, snapshot.itemLevel());
        setNullableBoolean(statement, index++, snapshot.identified());
        setNullableBoolean(statement, index++, snapshot.corrupted());
        setNullableBoolean(statement, index++, snapshot.fractured());
        setNullableBoolean(statement, index++, snapshot.synthesised());
        setNullableBoolean(statement, index++, snapshot.mirrored());
        setNullableString(statement, index++, snapshot.listingMethod());
        setNullableInstant(statement, index++, snapshot.indexedAt());
        setNullableString(statement, index++, snapshot.priceType());
        statement.setBigDecimal(index++, snapshot.priceAmount());
        statement.setString(index++, snapshot.priceCurrency());
        statement.setString(index++, snapshot.rawItemJson());
        statement.setString(index++, snapshot.rawListingJson());
        statement.setString(index++, snapshot.rawResultJson());
        statement.setTimestamp(index, Timestamp.from(snapshot.collectedAt()));
    }

    private void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void setNullableBoolean(PreparedStatement statement, int index, Boolean value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BOOLEAN);
        } else {
            statement.setBoolean(index, value);
        }
    }

    private void setNullableInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }
}
