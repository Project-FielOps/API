package com.fieldops.shared.seed;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the E2E readiness endpoint closed until Flyway, JPA and the local seed
 * have completed. Spring's readiness state can become accepting just before
 * CommandLineRunner callbacks execute.
 */
@Component("e2eSeed")
@Profile("e2e")
public class E2eSeedReadiness implements HealthIndicator {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void markReady() {
        ready.set(true);
    }

    @Override
    public Health health() {
        return ready.get()
                ? Health.up().withDetail("seed", "ready").build()
                : Health.outOfService().withDetail("seed", "initializing").build();
    }
}
