package com.hellblade.poepricer.collector.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("poe.collector")
public record CollectorProperties(
        boolean enabled,
        String realm,
        String league,
        String streamKey,
        Duration fallbackRequestDelay,
        Duration idleDelay,
        Duration maxBackoff,
        boolean onlyPriced
) {

    private static final Set<String> SUPPORTED_REALMS = Set.of("pc", "xbox", "sony");

    public void validate() {
        requireText(realm, "POE_REALM");
        requireText(league, "POE_LEAGUE");
        requireText(streamKey, "POE_STREAM_KEY");
        requirePositive(fallbackRequestDelay, "POE_FALLBACK_REQUEST_DELAY");
        requirePositive(idleDelay, "POE_IDLE_DELAY");
        requirePositive(maxBackoff, "POE_MAX_BACKOFF");

        if (!SUPPORTED_REALMS.contains(normalizedRealm())) {
            throw new IllegalStateException("POE_REALM must be pc, xbox or sony");
        }
    }

    public String normalizedRealm() {
        return realm.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireText(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be configured");
        }
    }

    private static void requirePositive(Duration value, String environmentVariable) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(environmentVariable + " must be positive");
        }
    }
}
