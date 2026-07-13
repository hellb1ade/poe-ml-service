package com.hellblade.poepricer.api.client;

import org.springframework.stereotype.Component;

import com.hellblade.poepricer.api.client.dto.MlPredictionRequest;
import com.hellblade.poepricer.api.client.dto.MlPredictionResponse;

@Component
public class StubMlClient implements MlClient {

    @Override
    public MlPredictionResponse predict(MlPredictionRequest request) {
        return new MlPredictionResponse(10.0, "divine", "stub-v1");
    }
}
