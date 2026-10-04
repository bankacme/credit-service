package com.bank.credit.infrastructure.adapter.out.rest;

import com.bank.credit.application.port.out.MovementRecorderPort;
import com.bank.credit.domain.exception.DownstreamServiceUnavailableException;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.OperationType;
import com.bank.credit.domain.model.ProductType;
import com.bank.credit.infrastructure.support.RxJavaReactorBridge;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * POST /transactions/records de transaction-service (interno, data-model 2.6), con circuit breaker y
 * time limiter de 2 s (instancia "transaction-service"). 201 (nuevo) y 200 (duplicado) cuentan como
 * registrado: transaction-service es idempotente por operationId. Cualquier otra respuesta o un
 * timeout es un error que HistoryRecorder se traga: la operación queda recorded = false y se
 * reintenta. En P3 lo reemplaza un no-op (el historial se alimenta de los eventos).
 */
@Component
public class MovementRecorderRestAdapter implements MovementRecorderPort {

    private static final String SERVICE = "transaction-service";

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;

    public MovementRecorderRestAdapter(WebClient.Builder builder,
                                       @Value("${bank.clients.transaction-service.base-url}") String baseUrl,
                                       CircuitBreakerRegistry circuitBreakerRegistry,
                                       TimeLimiterRegistry timeLimiterRegistry) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(SERVICE);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(SERVICE);
    }

    @Override
    public Completable record(CreditOperation operation) {
        Mono<Void> call = webClient.post()
                .uri("/transactions/records")
                .bodyValue(RecordRequest.of(operation))
                .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                        ? response.releaseBody()
                        : response.<Void>createError())
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(error -> new DownstreamServiceUnavailableException(SERVICE, error));
        return RxJavaReactorBridge.toCompletable(call);
    }

    /** Tipo en el historial: crédito + pago → CREDIT_PAYMENT; tarjeta + pago → CARD_PAYMENT; consumo → CARD_CHARGE. */
    static String movementType(CreditOperation operation) {
        if (operation.type() == OperationType.CHARGE) {
            return "CARD_CHARGE";
        }
        return operation.productType() == ProductType.CREDIT ? "CREDIT_PAYMENT" : "CARD_PAYMENT";
    }

    /** Cuerpo de RecordExternalMovementRequest (contrato de transaction-service). */
    record RecordRequest(String operationId, String productType, String productId, String customerId, String type,
                         BigDecimal amount, BigDecimal resultingBalance, String payerCustomerId, String description,
                         Instant occurredAt) {

        static RecordRequest of(CreditOperation operation) {
            return new RecordRequest(operation.operationId().value(), operation.productType().name(),
                    operation.productId(), operation.customerId(), movementType(operation),
                    operation.amount().amount(), operation.resultingBalance().amount(),
                    operation.payerCustomerId(), operation.description(), operation.occurredAt());
        }
    }
}
