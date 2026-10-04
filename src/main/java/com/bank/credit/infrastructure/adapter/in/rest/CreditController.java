package com.bank.credit.infrastructure.adapter.in.rest;

import com.bank.credit.application.port.in.CloseCreditUseCase;
import com.bank.credit.application.port.in.FindCreditUseCase;
import com.bank.credit.application.port.in.FindCreditsUseCase;
import com.bank.credit.application.port.in.GetPaymentInfoUseCase;
import com.bank.credit.application.port.in.OpenCreditUseCase;
import com.bank.credit.application.port.in.PayCreditUseCase;
import com.bank.credit.application.port.in.RescheduleCreditUseCase;
import com.bank.credit.domain.model.CreditId;
import com.bank.credit.domain.model.ProductType;
import com.bank.credit.infrastructure.adapter.in.rest.api.CreditsApi;
import com.bank.credit.infrastructure.adapter.in.rest.dto.Credit;
import com.bank.credit.infrastructure.adapter.in.rest.dto.CreditStatus;
import com.bank.credit.infrastructure.adapter.in.rest.dto.OpenCreditRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.OwnerType;
import com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentInfo;
import com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentResult;
import com.bank.credit.infrastructure.adapter.in.rest.dto.RescheduleCreditRequest;
import com.bank.credit.infrastructure.mapper.CreditRestMapper;
import com.bank.credit.infrastructure.support.RxJavaReactorBridge;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tag "Credits". Solo traduce: DTO → comando, caso de uso (RxJava) → Mono, resultado → DTO. */
@RestController
public class CreditController implements CreditsApi {

    private final OpenCreditUseCase openCreditUseCase;
    private final FindCreditsUseCase findCreditsUseCase;
    private final FindCreditUseCase findCreditUseCase;
    private final RescheduleCreditUseCase rescheduleCreditUseCase;
    private final CloseCreditUseCase closeCreditUseCase;
    private final PayCreditUseCase payCreditUseCase;
    private final GetPaymentInfoUseCase getPaymentInfoUseCase;
    private final CreditRestMapper mapper;

    public CreditController(OpenCreditUseCase openCreditUseCase, FindCreditsUseCase findCreditsUseCase,
                            FindCreditUseCase findCreditUseCase, RescheduleCreditUseCase rescheduleCreditUseCase,
                            CloseCreditUseCase closeCreditUseCase, PayCreditUseCase payCreditUseCase,
                            GetPaymentInfoUseCase getPaymentInfoUseCase, CreditRestMapper mapper) {
        this.openCreditUseCase = openCreditUseCase;
        this.findCreditsUseCase = findCreditsUseCase;
        this.findCreditUseCase = findCreditUseCase;
        this.rescheduleCreditUseCase = rescheduleCreditUseCase;
        this.closeCreditUseCase = closeCreditUseCase;
        this.payCreditUseCase = payCreditUseCase;
        this.getPaymentInfoUseCase = getPaymentInfoUseCase;
        this.mapper = mapper;
    }

    @Override
    public Mono<ResponseEntity<Flux<Credit>>> listCredits(String customerId, OwnerType ownerType,
                                                          CreditStatus status, ServerWebExchange exchange) {
        Flux<Credit> credits = RxJavaReactorBridge
                .toFlux(findCreditsUseCase.execute(mapper.toFilter(customerId, ownerType, status)))
                .map(mapper::toDto);
        return Mono.just(ResponseEntity.ok(credits));
    }

    @Override
    public Mono<ResponseEntity<Credit>> openCredit(Mono<OpenCreditRequest> openCreditRequest,
                                                   ServerWebExchange exchange) {
        return openCreditRequest
                .map(mapper::toCommand)
                .flatMap(command -> RxJavaReactorBridge.toMono(openCreditUseCase.execute(command)))
                .map(credit -> ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(credit)));
    }

    @Override
    public Mono<ResponseEntity<Credit>> getCredit(String id, ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(findCreditUseCase.execute(new CreditId(id)))
                .map(credit -> ResponseEntity.ok(mapper.toDto(credit)));
    }

    @Override
    public Mono<ResponseEntity<Credit>> rescheduleCredit(String id, Mono<RescheduleCreditRequest> request,
                                                         ServerWebExchange exchange) {
        return request
                .map(body -> mapper.toCommand(id, body))
                .flatMap(command -> RxJavaReactorBridge.toMono(rescheduleCreditUseCase.execute(command)))
                .map(credit -> ResponseEntity.ok(mapper.toDto(credit)));
    }

    @Override
    public Mono<ResponseEntity<Void>> closeCredit(String id, ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(closeCreditUseCase.execute(new CreditId(id)))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @Override
    public Mono<ResponseEntity<PaymentResult>> payCredit(String id, Mono<PaymentRequest> paymentRequest,
                                                         ServerWebExchange exchange) {
        return paymentRequest
                .map(body -> mapper.toCommand(id, body))
                .flatMap(command -> RxJavaReactorBridge.toMono(payCreditUseCase.execute(command)))
                .map(result -> ResponseEntity.ok(mapper.toDto(result)));
    }

    @Override
    public Mono<ResponseEntity<PaymentInfo>> getCreditPaymentInfo(String id, ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(getPaymentInfoUseCase.execute(ProductType.CREDIT, id))
                .map(view -> ResponseEntity.ok(mapper.toDto(view)));
    }
}
