package com.hellblade.poepricer.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hellblade.poepricer.api.dto.PriceRequest;
import com.hellblade.poepricer.api.dto.PriceResponse;

@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    @PostMapping("/estimate")
    public PriceResponse estimate(@RequestBody PriceRequest request) {
        return new PriceResponse(10.0, "divine");
    }
}
