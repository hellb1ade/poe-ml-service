package com.hellblade.poepricer.api.client;

import com.hellblade.poepricer.api.client.dto.MlPredictionRequest;
import com.hellblade.poepricer.api.client.dto.MlPredictionResponse;

public interface MlClient {

    MlPredictionResponse predict(MlPredictionRequest request);
}
