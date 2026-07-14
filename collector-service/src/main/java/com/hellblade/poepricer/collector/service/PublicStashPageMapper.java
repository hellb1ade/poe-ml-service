package com.hellblade.poepricer.collector.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hellblade.poepricer.collector.client.dto.PublicStashResponse;
import com.hellblade.poepricer.collector.config.CollectorProperties;
import com.hellblade.poepricer.collector.model.MappedStashPage;
import com.hellblade.poepricer.collector.model.MarketItemSnapshot;
import com.hellblade.poepricer.collector.model.StashObservation;
import com.hellblade.poepricer.collector.price.ListingPrice;
import com.hellblade.poepricer.collector.price.PriceParser;

import tools.jackson.databind.JsonNode;

@Component
public class PublicStashPageMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicStashPageMapper.class);

    private final CollectorProperties properties;
    private final PriceParser priceParser;

    public PublicStashPageMapper(CollectorProperties properties, PriceParser priceParser) {
        this.properties = properties;
        this.priceParser = priceParser;
    }

    public MappedStashPage map(PublicStashResponse response) {
        List<StashObservation> observations = new ArrayList<>();
        int targetStashCount = 0;
        int eligibleItemCount = 0;

        for (JsonNode stash : response.stashes()) {
            String stashId = text(stash, "id");
            if (stashId == null) {
                LOGGER.warn("Skipping a Public Stash change without an id");
                continue;
            }

            boolean publicStash = bool(stash, "public");
            if (!publicStash) {
                observations.add(new StashObservation(stashId, false, null, null, null, List.of()));
                continue;
            }

            String league = text(stash, "league");
            if (!properties.league().equals(league)) {
                continue;
            }

            targetStashCount++;
            String stashName = text(stash, "stash");
            List<MarketItemSnapshot> items = mapItems(stash.get("items"), league, stashName);
            eligibleItemCount += items.size();
            observations.add(new StashObservation(
                    stashId,
                    true,
                    league,
                    stashName,
                    text(stash, "stashType"),
                    items
            ));
        }

        return new MappedStashPage(
                response.nextChangeId(),
                response.stashes().size(),
                targetStashCount,
                eligibleItemCount,
                observations
        );
    }

    private List<MarketItemSnapshot> mapItems(JsonNode itemsNode, String league, String stashName) {
        if (itemsNode == null || !itemsNode.isArray()) {
            return List.of();
        }

        List<MarketItemSnapshot> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            if (!"Rare".equals(text(item, "rarity"))) {
                continue;
            }

            ListingPrice price = priceParser
                    .effectivePrice(text(item, "note"), stashName)
                    .orElse(null);
            if (properties.onlyPriced() && price == null) {
                continue;
            }

            String rawJson = item.toString();
            String itemId = text(item, "id");
            items.add(new MarketItemSnapshot(
                    itemId == null ? sha256(rawJson) : itemId,
                    itemId,
                    valueOrDefault(text(item, "league"), league),
                    valueOrDefault(text(item, "realm"), properties.normalizedRealm()),
                    text(item, "name"),
                    text(item, "typeLine"),
                    text(item, "baseType"),
                    text(item, "rarity"),
                    integer(item, "ilvl", "itemLevel"),
                    bool(item, "identified"),
                    bool(item, "corrupted"),
                    bool(item, "fractured"),
                    bool(item, "synthesised"),
                    influence(item, "elder"),
                    influence(item, "shaper"),
                    influence(item, "searing"),
                    influence(item, "tangled"),
                    integer(item, "w"),
                    integer(item, "h"),
                    integer(item, "x"),
                    integer(item, "y"),
                    price,
                    rawJson
            ));
        }
        return items;
    }

    private boolean influence(JsonNode item, String influence) {
        if (bool(item, influence)) {
            return true;
        }
        JsonNode influences = item.get("influences");
        return influences != null && influences.isObject() && bool(influences, influence);
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isString() ? null : value.asString();
    }

    private boolean bool(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return false;
        }
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.asBoolean(false);
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

    private String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
