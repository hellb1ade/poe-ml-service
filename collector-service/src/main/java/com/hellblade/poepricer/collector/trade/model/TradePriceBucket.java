package com.hellblade.poepricer.collector.trade.model;

import java.math.BigDecimal;

public record TradePriceBucket(
        String key,
        String quotaGroup,
        BigDecimal minimum,
        BigDecimal maximum,
        Integer minimumItemLevel,
        Integer maximumItemLevel
) {

    public TradePriceBucket(
            String key,
            String quotaGroup,
            BigDecimal minimum,
            BigDecimal maximum
    ) {
        this(key, quotaGroup, minimum, maximum, null, null);
    }
}
