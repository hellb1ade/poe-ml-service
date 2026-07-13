package com.hellblade.poepricer.api.client.trade.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TradeSearchRequest(
        Query query,
        Sort sort
) {

    public static TradeSearchRequest byName(String itemName, SellerStatus sellerStatus) {
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("Item name is required");
        }
        if (sellerStatus == null) {
            throw new IllegalArgumentException("Seller status is required");
        }

        return new TradeSearchRequest(
                new Query(new Status(sellerStatus.apiValue()), itemName),
                new Sort("asc")
        );
    }

    public enum SellerStatus {
        ONLINE("online"),
        ANY("any");

        private final String apiValue;

        SellerStatus(String apiValue) {
            this.apiValue = apiValue;
        }

        public String apiValue() {
            return apiValue;
        }
    }

    public record Query(Status status, String name) {
    }

    public record Status(String option) {
    }

    public record Sort(String price) {
    }
}
