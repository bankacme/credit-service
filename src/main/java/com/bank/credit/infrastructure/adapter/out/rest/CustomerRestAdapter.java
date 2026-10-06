package com.bank.credit.infrastructure.adapter.out.rest;

import com.bank.credit.application.port.out.CustomerLookupPort;
import com.bank.credit.domain.exception.DownstreamServiceUnavailableException;
import com.bank.credit.domain.model.CustomerSnapshot;
import com.bank.credit.domain.model.CustomerType;
import com.bank.credit.infrastructure.support.RxJavaReactorBridge;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * GET /customers/{id} de customer-service (P1/P2), con circuit breaker y time limiter de 2 s
 * (instancia "customer-service"). 404 → vacío (la cadena de AcquisitionPolicy lo vuelve 404
 * CUSTOMER_NOT_FOUND); cualquier otro fallo (timeout, circuito abierto, 5xx) → 503. Mismo patrón que
 * el CustomerServiceClient de account-service. En P3 lo reemplaza un read model.
 */
@Component
public class CustomerRestAdapter implements CustomerLookupPort {

    private static final String SERVICE = "customer-service";

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;

    public CustomerRestAdapter(@LoadBalanced WebClient.Builder builder,
                               @Value("${bank.clients.customer-service.base-url}") String baseUrl,
                               CircuitBreakerRegistry circuitBreakerRegistry, TimeLimiterRegistry timeLimiterRegistry) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(SERVICE);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(SERVICE);
    }

    @Override
    public Maybe<CustomerSnapshot> findById(String customerId) {
        Mono<CustomerSnapshot> call = webClient.get()
                .uri("/customers/{id}", customerId)
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.NOT_FOUND)) {
                        return Mono.<CustomerResponse>empty();
                    }
                    if (response.statusCode().isError()) {
                        return response.<CustomerResponse>createError();
                    }
                    return response.bodyToMono(CustomerResponse.class);
                })
                .map(response -> new CustomerSnapshot(response.id(), response.type(), response.status()))
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(error -> new DownstreamServiceUnavailableException(SERVICE, error));
        return RxJavaReactorBridge.toMaybe(call);
    }

    /** Solo los campos que importan aquí; Jackson ignora el resto del Customer. */
    private record CustomerResponse(String id, CustomerType type, String status) {
    }
}
