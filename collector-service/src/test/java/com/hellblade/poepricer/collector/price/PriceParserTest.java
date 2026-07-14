package com.hellblade.poepricer.collector.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PriceParserTest {

    private final PriceParser parser = new PriceParser();

    @Test
    void itemPriceTakesPrecedenceOverStashPrice() {
        ListingPrice price = parser.effectivePrice(
                "~price 2 divine",
                "~price 10 chaos"
        ).orElseThrow();

        assertEquals(new BigDecimal("2"), price.amount());
        assertEquals("divine", price.currency());
        assertEquals("price", price.type());
        assertEquals("item", price.source());
    }

    @Test
    void fallsBackToStashWideBuyoutAndParsesFraction() {
        ListingPrice price = parser.effectivePrice(
                null,
                "~b/o 1/2 divine"
        ).orElseThrow();

        assertEquals(0, new BigDecimal("0.5").compareTo(price.amount()));
        assertEquals("divine", price.currency());
        assertEquals("b/o", price.type());
        assertEquals("stash", price.source());
    }

    @Test
    void rejectsMalformedOrNonPositivePrices() {
        assertTrue(parser.effectivePrice("negotiable", null).isEmpty());
        assertTrue(parser.effectivePrice("~price 0 chaos", null).isEmpty());
        assertTrue(parser.effectivePrice("~price 1/0 divine", null).isEmpty());
    }
}
