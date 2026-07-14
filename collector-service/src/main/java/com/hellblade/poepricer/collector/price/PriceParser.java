package com.hellblade.poepricer.collector.price;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class PriceParser {

    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "^\\s*~(?<type>price|b/o)\\s+"
                    + "(?<amount>\\d+(?:\\.\\d+)?(?:/\\d+(?:\\.\\d+)?)?)\\s+"
                    + "(?<currency>[A-Za-z][A-Za-z0-9-]*)\\b.*$",
            Pattern.CASE_INSENSITIVE
    );

    public Optional<ListingPrice> effectivePrice(String itemNote, String stashName) {
        Optional<ListingPrice> itemPrice = parse(itemNote, "item");
        return itemPrice.isPresent() ? itemPrice : parse(stashName, "stash");
    }

    Optional<ListingPrice> parse(String note, String source) {
        if (note == null || note.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = PRICE_PATTERN.matcher(note);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        BigDecimal amount = parseAmount(matcher.group("amount"));
        if (amount == null || amount.signum() <= 0) {
            return Optional.empty();
        }

        return Optional.of(new ListingPrice(
                amount.stripTrailingZeros(),
                matcher.group("currency").toLowerCase(Locale.ROOT),
                matcher.group("type").toLowerCase(Locale.ROOT),
                source,
                note
        ));
    }

    private BigDecimal parseAmount(String value) {
        try {
            int separator = value.indexOf('/');
            if (separator < 0) {
                return new BigDecimal(value);
            }

            BigDecimal numerator = new BigDecimal(value.substring(0, separator));
            BigDecimal denominator = new BigDecimal(value.substring(separator + 1));
            if (denominator.signum() == 0) {
                return null;
            }
            return numerator.divide(denominator, MathContext.DECIMAL128);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
