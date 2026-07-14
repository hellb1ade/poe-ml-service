package com.hellblade.poepricer.collector.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class PoeRateLimitGateTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-14T10:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void waitsForTheFullWindowWhenAClientBucketIsAlmostExhausted() {
        PoeRateLimitGate gate = new PoeRateLimitGate(CLOCK);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Rate-Limit-Rules", "client");
        headers.set("X-Rate-Limit-Client", "10:5:10");
        headers.set("X-Rate-Limit-Client-State", "9:5:0");

        gate.update(headers);

        assertEquals(Duration.ofSeconds(5), gate.remainingDelay());
    }

    @Test
    void usesTheLongestRestrictionAcrossHeaders() {
        PoeRateLimitGate gate = new PoeRateLimitGate(CLOCK);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Rate-Limit-Rules", "client,ip");
        headers.set("X-Rate-Limit-Client", "10:5:10");
        headers.set("X-Rate-Limit-Client-State", "2:5:0");
        headers.set("X-Rate-Limit-IP", "20:10:30");
        headers.set("X-Rate-Limit-IP-State", "21:10:12");
        headers.set(HttpHeaders.RETRY_AFTER, "7");

        gate.update(headers);

        assertEquals(Duration.ofSeconds(12), gate.remainingDelay());
    }
}
