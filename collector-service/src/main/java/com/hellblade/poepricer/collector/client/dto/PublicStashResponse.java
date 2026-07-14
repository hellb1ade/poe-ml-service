package com.hellblade.poepricer.collector.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicStashResponse(
        @JsonProperty("next_change_id") String nextChangeId,
        List<JsonNode> stashes
) {

    public PublicStashResponse {
        stashes = stashes == null ? List.of() : List.copyOf(stashes);
    }
}
