package com.hellblade.poepricer.collector.trade.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeSearchResponse(
        String id,
        Integer complexity,
        List<String> result,
        Integer total
) {

    public TradeSearchResponse {
        result = result == null ? List.of() : List.copyOf(result);
    }
}
