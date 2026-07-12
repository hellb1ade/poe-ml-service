package com.hellblade.poepricer.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PriceResponse {

    private double price;
    private String currency;
}
