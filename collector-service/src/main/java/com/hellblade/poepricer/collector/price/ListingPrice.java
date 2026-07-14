package com.hellblade.poepricer.collector.price;

import java.math.BigDecimal;

public record ListingPrice(
        BigDecimal amount,
        String currency,
        String type,
        String source,
        String note
) {
}
