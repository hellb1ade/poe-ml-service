package com.hellblade.poepricer.api.client.dto;

public record MlPredictionResponse(
        double predictedPrice,
        String currency,
        String modelVersion
) {
}
