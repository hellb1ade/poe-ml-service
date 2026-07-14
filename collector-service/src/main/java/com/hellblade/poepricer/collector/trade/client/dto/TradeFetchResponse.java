package com.hellblade.poepricer.collector.trade.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeFetchResponse(List<JsonNode> result) {

    public TradeFetchResponse {
        result = result == null ? List.of() : List.copyOf(result);
    }
}
