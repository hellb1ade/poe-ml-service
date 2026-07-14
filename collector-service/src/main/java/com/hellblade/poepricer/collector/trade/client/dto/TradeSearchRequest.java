package com.hellblade.poepricer.collector.trade.client.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TradeSearchRequest(Query query, Sort sort) {

    public static TradeSearchRequest rareItems(
            String availability,
            String category,
            String priceCurrency,
            BigDecimal minimumPrice,
            BigDecimal maximumPrice
    ) {
        return rareItems(
                availability,
                category,
                priceCurrency,
                minimumPrice,
                maximumPrice,
                null,
                null
        );
    }

    public static TradeSearchRequest rareItems(
            String availability,
            String category,
            String priceCurrency,
            BigDecimal minimumPrice,
            BigDecimal maximumPrice,
            Integer minimumItemLevel,
            Integer maximumItemLevel
    ) {
        return new TradeSearchRequest(
                new Query(
                        new Status(availability),
                        List.of(new StatGroup("and", List.of())),
                        new Filters(
                                new TypeFilters(new TypeFilterValues(
                                        new Option("rare"),
                                        new Option(category)
                                )),
                                new TradeFilters(new TradeFilterValues(
                                        new Option("any"),
                                        new PriceFilter(
                                                priceCurrency,
                                                minimumPrice,
                                                maximumPrice
                                        )
                                )),
                                minimumItemLevel == null && maximumItemLevel == null
                                        ? null
                                        : new MiscFilters(new MiscFilterValues(
                                                new NumericFilter(
                                                        minimumItemLevel,
                                                        maximumItemLevel
                                                )
                                        ))
                        )
                ),
                new Sort("asc")
        );
    }

    public record Query(Status status, List<StatGroup> stats, Filters filters) {
    }

    public record Status(String option) {
    }

    public record StatGroup(String type, List<Object> filters) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Filters(
            @JsonProperty("type_filters") TypeFilters typeFilters,
            @JsonProperty("trade_filters") TradeFilters tradeFilters,
            @JsonProperty("misc_filters") MiscFilters miscFilters
    ) {
    }

    public record TypeFilters(TypeFilterValues filters) {
    }

    public record TypeFilterValues(Option rarity, Option category) {
    }

    public record TradeFilters(TradeFilterValues filters) {
    }

    public record TradeFilterValues(
            @JsonProperty("sale_type") Option saleType,
            PriceFilter price
    ) {
    }

    public record Option(String option) {
    }

    public record PriceFilter(String option, BigDecimal min, BigDecimal max) {
    }

    public record MiscFilters(MiscFilterValues filters) {
    }

    public record MiscFilterValues(NumericFilter ilvl) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NumericFilter(Integer min, Integer max) {
    }

    public record Sort(String price) {
    }
}
