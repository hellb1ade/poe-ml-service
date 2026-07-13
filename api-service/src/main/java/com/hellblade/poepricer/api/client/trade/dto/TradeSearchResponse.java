package com.hellblade.poepricer.api.client.trade.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TradeSearchResponse(
        String id,
        Integer complexity,
        List<String> result,
        Integer total
) {
}
