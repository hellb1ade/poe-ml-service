package com.hellblade.poepricer.collector.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpHeaders;

public final class PoeRateLimitGate {

    private final Clock clock;
    private final AtomicReference<Instant> blockedUntil =
            new AtomicReference<>(Instant.EPOCH);

    public PoeRateLimitGate() {
        this(Clock.systemUTC());
    }

    PoeRateLimitGate(Clock clock) {
        this.clock = clock;
    }

    public void update(HttpHeaders headers) {
        Instant now = clock.instant();
        defer(parseRetryAfter(headers), now);

        for (String rule : split(headers.getFirst("X-Rate-Limit-Rules"))) {
            String headerSuffix = rule.trim().toLowerCase(Locale.ROOT);
            String limitValue = headers.getFirst("X-Rate-Limit-" + headerSuffix);
            String stateValue = headers.getFirst("X-Rate-Limit-" + headerSuffix + "-State");
            updateRule(limitValue, stateValue, now);
        }
    }

    public Duration remainingDelay() {
        Duration remaining = Duration.between(clock.instant(), blockedUntil.get());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private void updateRule(String limitValue, String stateValue, Instant now) {
        List<String> limits = split(limitValue);
        List<String> states = split(stateValue);
        int count = Math.min(limits.size(), states.size());

        for (int index = 0; index < count; index++) {
            long[] limit = parseTriplet(limits.get(index));
            long[] state = parseTriplet(states.get(index));
            if (limit == null || state == null) {
                continue;
            }

            long activeRestrictionSeconds = state[2];
            if (activeRestrictionSeconds > 0) {
                defer(Duration.ofSeconds(activeRestrictionSeconds), now);
            } else if (state[0] >= Math.max(1, limit[0] - 1)) {
                // The exact window start is not exposed, so waiting one full period is conservative.
                defer(Duration.ofSeconds(limit[1]), now);
            }
        }
    }

    private void defer(Duration duration, Instant now) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        Instant candidate = now.plus(duration);
        blockedUntil.accumulateAndGet(candidate, (current, update) ->
                current.isAfter(update) ? current : update);
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return Duration.ZERO;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            return Duration.ZERO;
        }
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private long[] parseTriplet(String value) {
        String[] parts = value.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new long[] {
                    Long.parseLong(parts[0]),
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2])
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
