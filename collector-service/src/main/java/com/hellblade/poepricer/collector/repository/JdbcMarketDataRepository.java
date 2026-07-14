package com.hellblade.poepricer.collector.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hellblade.poepricer.collector.model.MappedStashPage;
import com.hellblade.poepricer.collector.model.MarketItemSnapshot;
import com.hellblade.poepricer.collector.model.StashObservation;
import com.hellblade.poepricer.collector.price.ListingPrice;

@Repository
@ConditionalOnProperty(prefix = "poe.collector", name = "enabled", havingValue = "true")
public class JdbcMarketDataRepository implements MarketDataRepository {

    private static final int BATCH_SIZE = 500;

    private static final String UPSERT_STASH_SQL = """
            insert into collector.market_stash (
                stash_id, public, league, stash_name, stash_type, last_change_id,
                first_seen_at, last_seen_at
            ) values (?, true, ?, ?, ?, ?, ?, ?)
            on conflict (stash_id) do update set
                public = true,
                league = excluded.league,
                stash_name = excluded.stash_name,
                stash_type = excluded.stash_type,
                last_change_id = excluded.last_change_id,
                last_seen_at = excluded.last_seen_at
            """;

    private static final String MARK_PRIVATE_SQL = """
            update collector.market_stash
               set public = false,
                   last_change_id = ?,
                   last_seen_at = ?
             where stash_id = ?
            """;

    private static final String INSERT_ITEM_SQL = """
            insert into collector.market_item_snapshot (
                change_id, stash_id, item_key, item_id, league, realm,
                item_name, type_line, base_type, rarity, item_level, identified,
                corrupted, fractured, synthesised, elder, shaper, searing, tangled,
                width, height, x, y,
                price_amount, price_currency, price_type, price_source, price_note,
                raw_item_json, collected_at
            ) values (
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                cast(? as jsonb), ?
            )
            on conflict (change_id, stash_id, item_key) do nothing
            """;

    private static final String INSERT_CURSOR_SQL = """
            insert into collector.collector_cursor (stream_key, next_change_id, updated_at)
            values (?, ?, ?)
            on conflict (stream_key) do nothing
            """;

    private static final String UPDATE_CURSOR_SQL = """
            update collector.collector_cursor
               set next_change_id = ?,
                   updated_at = ?
             where stream_key = ?
               and next_change_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcMarketDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> findCursor(String streamKey) {
        List<String> cursors = jdbcTemplate.query(
                "select next_change_id from collector.collector_cursor where stream_key = ?",
                (resultSet, rowNumber) -> resultSet.getString(1),
                streamKey
        );
        return cursors.stream().findFirst();
    }

    @Override
    @Transactional
    public void savePage(
            String streamKey,
            String expectedCursor,
            MappedStashPage page,
            Instant collectedAt
    ) {
        Timestamp timestamp = Timestamp.from(collectedAt);
        List<StashObservation> publicStashes = page.stashes().stream()
                .filter(StashObservation::publicStash)
                .toList();
        List<StashObservation> privateStashes = page.stashes().stream()
                .filter(stash -> !stash.publicStash())
                .toList();

        jdbcTemplate.batchUpdate(
                UPSERT_STASH_SQL,
                publicStashes,
                BATCH_SIZE,
                (statement, stash) -> bindStash(statement, stash, page.nextChangeId(), timestamp)
        );
        jdbcTemplate.batchUpdate(
                MARK_PRIVATE_SQL,
                privateStashes,
                BATCH_SIZE,
                (statement, stash) -> {
                    statement.setString(1, page.nextChangeId());
                    statement.setTimestamp(2, timestamp);
                    statement.setString(3, stash.stashId());
                }
        );

        List<ItemRow> itemRows = publicStashes.stream()
                .flatMap(stash -> stash.items().stream().map(item -> new ItemRow(stash.stashId(), item)))
                .toList();
        jdbcTemplate.batchUpdate(
                INSERT_ITEM_SQL,
                itemRows,
                BATCH_SIZE,
                (statement, row) -> bindItem(
                        statement,
                        page.nextChangeId(),
                        row.stashId(),
                        row.item(),
                        timestamp
                )
        );

        advanceCursor(streamKey, expectedCursor, page.nextChangeId(), timestamp);
    }

    private void advanceCursor(
            String streamKey,
            String expectedCursor,
            String nextCursor,
            Timestamp timestamp
    ) {
        int updated;
        if (expectedCursor == null) {
            updated = jdbcTemplate.update(
                    INSERT_CURSOR_SQL,
                    streamKey,
                    nextCursor,
                    timestamp
            );
        } else {
            updated = jdbcTemplate.update(
                    UPDATE_CURSOR_SQL,
                    nextCursor,
                    timestamp,
                    streamKey,
                    expectedCursor
            );
        }

        if (updated != 1) {
            throw new CursorConflictException(streamKey);
        }
    }

    private void bindStash(
            PreparedStatement statement,
            StashObservation stash,
            String changeId,
            Timestamp timestamp
    ) throws SQLException {
        statement.setString(1, stash.stashId());
        statement.setString(2, stash.league());
        statement.setString(3, stash.stashName());
        statement.setString(4, stash.stashType());
        statement.setString(5, changeId);
        statement.setTimestamp(6, timestamp);
        statement.setTimestamp(7, timestamp);
    }

    private void bindItem(
            PreparedStatement statement,
            String changeId,
            String stashId,
            MarketItemSnapshot item,
            Timestamp timestamp
    ) throws SQLException {
        int index = 1;
        statement.setString(index++, changeId);
        statement.setString(index++, stashId);
        statement.setString(index++, item.itemKey());
        setNullableString(statement, index++, item.itemId());
        statement.setString(index++, item.league());
        setNullableString(statement, index++, item.realm());
        setNullableString(statement, index++, item.itemName());
        setNullableString(statement, index++, item.typeLine());
        setNullableString(statement, index++, item.baseType());
        statement.setString(index++, item.rarity());
        setNullableInteger(statement, index++, item.itemLevel());
        statement.setBoolean(index++, item.identified());
        statement.setBoolean(index++, item.corrupted());
        statement.setBoolean(index++, item.fractured());
        statement.setBoolean(index++, item.synthesised());
        statement.setBoolean(index++, item.elder());
        statement.setBoolean(index++, item.shaper());
        statement.setBoolean(index++, item.searing());
        statement.setBoolean(index++, item.tangled());
        setNullableInteger(statement, index++, item.width());
        setNullableInteger(statement, index++, item.height());
        setNullableInteger(statement, index++, item.x());
        setNullableInteger(statement, index++, item.y());

        ListingPrice price = item.price();
        if (price == null) {
            statement.setNull(index++, Types.NUMERIC);
            statement.setNull(index++, Types.VARCHAR);
            statement.setNull(index++, Types.VARCHAR);
            statement.setNull(index++, Types.VARCHAR);
            statement.setNull(index++, Types.VARCHAR);
        } else {
            statement.setBigDecimal(index++, price.amount());
            statement.setString(index++, price.currency());
            statement.setString(index++, price.type());
            statement.setString(index++, price.source());
            statement.setString(index++, price.note());
        }

        statement.setString(index++, item.rawItemJson());
        statement.setTimestamp(index, timestamp);
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

    private record ItemRow(String stashId, MarketItemSnapshot item) {
    }
}
