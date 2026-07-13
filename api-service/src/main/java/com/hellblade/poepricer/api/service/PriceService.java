package com.hellblade.poepricer.api.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.hellblade.poepricer.api.client.MlClient;
import com.hellblade.poepricer.api.client.dto.MlPredictionRequest;
import com.hellblade.poepricer.api.client.dto.MlPredictionResponse;
import com.hellblade.poepricer.api.dto.PriceRequest;
import com.hellblade.poepricer.api.dto.PriceResponse;

@Service
@RequiredArgsConstructor
public class PriceService {

    private final MlClient mlClient;

    public PriceResponse estimate(PriceRequest request) {
        MlPredictionRequest predictionRequest = new MlPredictionRequest(
                request.getLeague(),
                request.getItemName(),
                request.getBaseType(),
                request.getItemLevel(),
                List.copyOf(request.getMods()),
                List.of()
        );
        MlPredictionResponse prediction = mlClient.predict(predictionRequest);

        return new PriceResponse(prediction.predictedPrice(), prediction.currency());
    }
}
