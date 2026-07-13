package com.hellblade.poepricer.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceRequest {

    @NotBlank(message = "League is required")
    private String league;

    @NotBlank(message = "Item name is required")
    private String itemName;

    @NotBlank(message = "Base type is required")
    private String baseType;

    @NotNull(message = "Item level is required")
    @Positive(message = "Item level must be positive")
    private Integer itemLevel;

    @NotNull(message = "Mods are required")
    @Size(min = 1, max = 20, message = "Mods must contain between 1 and 20 entries")
    private List<@NotBlank(message = "Mod must not be blank") String> mods;
}
