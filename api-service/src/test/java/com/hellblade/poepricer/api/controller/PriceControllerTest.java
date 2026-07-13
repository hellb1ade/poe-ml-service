package com.hellblade.poepricer.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.hellblade.poepricer.api.client.MlClient;
import com.hellblade.poepricer.api.client.dto.MlPredictionRequest;
import com.hellblade.poepricer.api.client.dto.MlPredictionResponse;
import com.hellblade.poepricer.api.exception.GlobalExceptionHandler;
import com.hellblade.poepricer.api.service.PriceService;

class PriceControllerTest {

    private MlClient mlClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mlClient = mock(MlClient.class);
        PriceService priceService = new PriceService(mlClient);
        PriceController priceController = new PriceController(priceService);

        mockMvc = MockMvcBuilders.standaloneSetup(priceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void estimateReturnsMlPrediction() throws Exception {
        when(mlClient.predict(any(MlPredictionRequest.class)))
                .thenReturn(new MlPredictionResponse(12.4, "divine", "mvp-001"));

        mockMvc.perform(post("/api/v1/prices/estimate")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "league": "Mirage",
                                  "itemName": "Doom Veil",
                                  "baseType": "Hubris Circlet",
                                  "itemLevel": 84,
                                  "mods": [
                                    "+98 to maximum Life",
                                    "+42% to Chaos Resistance"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(12.4))
                .andExpect(jsonPath("$.currency").value("divine"));

        ArgumentCaptor<MlPredictionRequest> requestCaptor =
                ArgumentCaptor.forClass(MlPredictionRequest.class);
        verify(mlClient).predict(requestCaptor.capture());

        MlPredictionRequest predictionRequest = requestCaptor.getValue();
        assertEquals("Mirage", predictionRequest.league());
        assertEquals("Hubris Circlet", predictionRequest.baseType());
        assertEquals(84, predictionRequest.itemLevel());
        assertEquals(2, predictionRequest.explicitMods().size());
        assertEquals(0, predictionRequest.fracturedMods().size());
    }

    @Test
    void estimateRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/prices/estimate")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "league": "",
                                  "itemName": "",
                                  "baseType": "",
                                  "itemLevel": 0,
                                  "mods": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.league").value("League is required"))
                .andExpect(jsonPath("$.fieldErrors.itemLevel")
                        .value("Item level must be positive"))
                .andExpect(jsonPath("$.fieldErrors.mods")
                        .value("Mods must contain between 1 and 20 entries"));

        verifyNoInteractions(mlClient);
    }
}
