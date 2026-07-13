package com.hellblade.poepricer.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hellblade.poepricer.api.dto.PriceRequest;
import com.hellblade.poepricer.api.dto.PriceResponse;
import com.hellblade.poepricer.api.service.PriceService;

@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @PostMapping("/estimate")
    public PriceResponse estimate(@Valid @RequestBody PriceRequest request) {
        return priceService.estimate(request);
    }
}
