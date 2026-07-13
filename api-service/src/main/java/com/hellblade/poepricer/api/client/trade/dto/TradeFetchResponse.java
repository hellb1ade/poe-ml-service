package com.hellblade.poepricer.api.client.trade.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeFetchResponse(List<Result> result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String id, Item item, Listing listing) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String id,
            String name,
            String typeLine,
            String baseType,
            String rarity,
            Integer ilvl,
            Boolean identified,
            Boolean corrupted,
            Boolean fractured,
            List<Mod> implicitMods,
            List<Mod> explicitMods,
            List<Mod> craftedMods,
            List<Mod> fracturedMods
    ) {
    }

    public record Mod(String description, String hash, JsonNode raw) {

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Mod(JsonNode raw) {
            this(description(raw), textField(raw, "hash"), raw);
        }

        private static String description(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            return node.isString() ? node.asString() : textField(node, "description");
        }

        private static String textField(JsonNode node, String fieldName) {
            if (node == null || !node.isObject()) {
                return null;
            }
            JsonNode value = node.get(fieldName);
            return value == null || value.isNull() ? null : value.asString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Listing(String method, String indexed, Price price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(String type, BigDecimal amount, String currency) {
    }
}
