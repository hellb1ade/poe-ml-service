package com.hellblade.poepricer.collector.service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.hellblade.poepricer.collector.client.PoeApiException;
import com.hellblade.poepricer.collector.client.PoeRateLimitGate;
import com.hellblade.poepricer.collector.config.CollectorProperties;
import com.hellblade.poepricer.collector.config.PoeApiProperties;
import com.hellblade.poepricer.collector.repository.CursorConflictException;

@Component
@ConditionalOnProperty(prefix = "poe.collector", name = "enabled", havingValue = "true")
public class CollectorLifecycle implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectorLifecycle.class);
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);

    private final PublicStashCollector collector;
    private final PoeRateLimitGate rateLimitGate;
    private final CollectorProperties collectorProperties;
    private final PoeApiProperties apiProperties;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile Thread worker;

    public CollectorLifecycle(
            PublicStashCollector collector,
            PoeRateLimitGate rateLimitGate,
            CollectorProperties collectorProperties,
            PoeApiProperties apiProperties
    ) {
        this.collector = collector;
        this.rateLimitGate = rateLimitGate;
        this.collectorProperties = collectorProperties;
        this.apiProperties = apiProperties;
    }

    @Override
    public void start() {
        collectorProperties.validate();
        apiProperties.validate();
        if (!running.compareAndSet(false, true)) {
            return;
        }

        worker = Thread.ofVirtual()
                .name("public-stash-collector")
                .start(this::runLoop);
    }

    @Override
    public void stop() {
        running.set(false);
        Thread currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.interrupt();
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        Thread currentWorker = worker;
        if (currentWorker == null || currentWorker == Thread.currentThread()) {
            callback.run();
            return;
        }

        Thread.ofVirtual().name("public-stash-collector-stop").start(() -> {
            try {
                currentWorker.join(Duration.ofSeconds(5));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                callback.run();
            }
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void runLoop() {
        String cursor = collector.currentCursor().orElse(null);
        Duration backoff = INITIAL_BACKOFF;
        LOGGER.info(
                "Public Stash collector started: league={}, realm={}, cursor={}",
                collectorProperties.league(),
                collectorProperties.normalizedRealm(),
                cursor
        );

        while (running.get()) {
            try {
                sleep(rateLimitGate.remainingDelay());
                CollectionOutcome outcome = collector.collectNextPage(cursor);
                cursor = outcome.nextChangeId();
                backoff = INITIAL_BACKOFF;

                Duration normalDelay = outcome.endOfStream()
                        ? collectorProperties.idleDelay()
                        : collectorProperties.fallbackRequestDelay();
                sleep(max(normalDelay, rateLimitGate.remainingDelay()));
            } catch (CursorConflictException exception) {
                cursor = collector.currentCursor().orElse(null);
                backoff = INITIAL_BACKOFF;
                LOGGER.warn(
                        "Collector cursor advanced by another instance; continuing from {}",
                        cursor
                );
            } catch (PoeApiException exception) {
                if (!exception.retryable()) {
                    LOGGER.error(
                            "Stopping Public Stash collector after a permanent API error: status={}",
                            exception.statusCode(),
                            exception
                    );
                    running.set(false);
                    break;
                }

                Duration delay = max(
                        withJitter(backoff),
                        exception.retryAfter(),
                        rateLimitGate.remainingDelay()
                );
                LOGGER.warn(
                        "Temporary Public Stash API failure: status={}, retrying in {} ms",
                        exception.statusCode(),
                        delay.toMillis()
                );
                sleep(delay);
                backoff = doubledAndCapped(backoff);
            } catch (RuntimeException exception) {
                Duration delay = withJitter(backoff);
                LOGGER.error(
                        "Collector page was not committed; cursor is unchanged. Retrying in {} ms",
                        delay.toMillis(),
                        exception
                );
                sleep(delay);
                backoff = doubledAndCapped(backoff);
            }
        }

        running.set(false);
        LOGGER.info("Public Stash collector stopped");
    }

    private Duration doubledAndCapped(Duration current) {
        Duration doubled;
        try {
            doubled = current.multipliedBy(2);
        } catch (ArithmeticException exception) {
            return collectorProperties.maxBackoff();
        }
        return doubled.compareTo(collectorProperties.maxBackoff()) > 0
                ? collectorProperties.maxBackoff()
                : doubled;
    }

    private Duration withJitter(Duration duration) {
        long jitterMillis = ThreadLocalRandom.current().nextLong(251);
        Duration candidate = duration.plusMillis(jitterMillis);
        return candidate.compareTo(collectorProperties.maxBackoff()) > 0
                ? collectorProperties.maxBackoff()
                : candidate;
    }

    private Duration max(Duration... durations) {
        Duration result = Duration.ZERO;
        for (Duration duration : durations) {
            if (duration != null && duration.compareTo(result) > 0) {
                result = duration;
            }
        }
        return result;
    }

    private void sleep(Duration duration) {
        if (!running.get() || duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }
}
