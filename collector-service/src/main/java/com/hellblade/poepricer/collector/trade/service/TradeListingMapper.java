package com.hellblade.poepricer.collector.trade.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.hellblade.poepricer.collector.trade.model.SampledListing;
import com.hellblade.poepricer.collector.trade.model.TradeListingSnapshot;

import tools.jackson.databind.JsonNode;

@Component
public class TradeListingMapper {

    public Optional<TradeListingSnapshot> map(
            UUID runId,
            String searchId,
            String league,
            SampledListing sampledListing,
            JsonNode result,
            Instant collectedAt
    ) {
        if (result == null || !result.isObject()) {
            return Optional.empty();
        }

        String listingId = text(result, "id");
        JsonNode item = object(result, "item");
        JsonNode listing = object(result, "listing");
        JsonNode price = object(listing, "price");
        BigDecimal amount = decimal(price, "amount");
        String currency = text(price, "currency");

        if (listingId == null || item == null || listing == null
                || amount == null || amount.signum() <= 0 || currency == null) {
            return Optional.empty();
        }

        return Optional.of(new TradeListingSnapshot(
                runId,
                sampledListing.ordinal(),
                sampledListing.searchResultRank(),
                sampledListing.priceQuantile(),
                sampledListing.priceBucket(),
                searchId,
                listingId,
                text(item, "id"),
                valueOrDefault(text(item, "league"), league),
                text(item, "name"),
                text(item, "typeLine"),
                text(item, "baseType"),
                text(item, "rarity"),
                integer(item, "ilvl", "itemLevel"),
                bool(item, "identified"),
                bool(item, "corrupted"),
                bool(item, "fractured"),
                bool(item, "synthesised"),
                bool(item, "mirrored"),
                text(listing, "method"),
                instant(text(listing, "indexed")),
                text(price, "type"),
                amount,
                currency,
                item.toString(),
                listing.toString(),
                result.toString(),
                collectedAt
        ));
    }

    private JsonNode object(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isObject() ? null : value;
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

    private BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.decimalValue();
    }

    private Integer integer(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isNumber()) {
                return value.asInt();
            }
        }
        return null;
    }

    private Boolean bool(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isBoolean()
                ? null
                : value.asBoolean();
    }

    private Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
