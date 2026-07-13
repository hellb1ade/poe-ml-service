package com.hellblade.poepricer.api.client.dto;

import java.util.List;

public record MlPredictionRequest(
        String league,
        String itemName,
        String baseType,
        Integer itemLevel,
        List<String> explicitMods,
        List<String> fracturedMods
) {
}
