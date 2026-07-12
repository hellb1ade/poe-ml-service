package com.hellblade.poepricer.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PriceRequest {

    private String itemName;
    private String baseType;
    private Integer itemLevel;
    private List<String> mods;
}
