package com.bank.credit.infrastructure.adapter.out.rest;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;

/** Los mismos valores que bank-config: ventana 4, umbral 50 %, 5 s abierto, timeout 2 s. */
final class ResilienceTestConfig {

    private ResilienceTestConfig() {
    }

    static CircuitBreakerRegistry circuitBreakers() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .build());
    }

    static TimeLimiterRegistry timeLimiters() {
        return TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .cancelRunningFuture(true)
                .build());
    }
}
