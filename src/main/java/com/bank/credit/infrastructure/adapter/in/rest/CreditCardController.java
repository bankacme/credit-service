package com.bank.credit.infrastructure.adapter.in.rest;

import com.bank.credit.application.port.in.ChangeCreditLimitUseCase;
import com.bank.credit.application.port.in.ChargeCreditCardUseCase;
import com.bank.credit.application.port.in.CloseCreditCardUseCase;
import com.bank.credit.application.port.in.FindCreditCardUseCase;
import com.bank.credit.application.port.in.FindCreditCardsUseCase;
import com.bank.credit.application.port.in.GetCreditCardBalanceUseCase;
import com.bank.credit.application.port.in.GetPaymentInfoUseCase;
import com.bank.credit.application.port.in.IssueCreditCardUseCase;
import com.bank.credit.application.port.in.PayCreditCardUseCase;
import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.domain.model.ProductType;
import com.bank.credit.infrastructure.adapter.in.rest.api.CreditCardsApi;
import com.bank.credit.infrastructure.adapter.in.rest.dto.CardBalance;
import com.bank.credit.infrastructure.adapter.in.rest.dto.CardStatus;
import com.bank.credit.infrastructure.adapter.in.rest.dto.ChangeLimitRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.ChargeRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.ChargeResult;
import com.bank.credit.infrastructure.adapter.in.rest.dto.CreditCard;
import com.bank.credit.infrastructure.adapter.in.rest.dto.IssueCreditCardRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentInfo;
import com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentResult;
import com.bank.credit.infrastructure.mapper.CreditRestMapper;
import com.bank.credit.infrastructure.support.RxJavaReactorBridge;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tag "Credit cards". */
@RestController
public class CreditCardController implements CreditCardsApi {

    private final IssueCreditCardUseCase issueUseCase;
    private final FindCreditCardsUseCase findCardsUseCase;
    private final FindCreditCardUseCase findCardUseCase;
    private final ChangeCreditLimitUseCase changeLimitUseCase;
    private final CloseCreditCardUseCase closeUseCase;
    private final GetCreditCardBalanceUseCase balanceUseCase;
    private final ChargeCreditCardUseCase chargeUseCase;
    private final PayCreditCardUseCase payUseCase;
    private final GetPaymentInfoUseCase paymentInfoUseCase;
    private final CreditRestMapper mapper;

    public CreditCardController(IssueCreditCardUseCase issueUseCase, FindCreditCardsUseCase findCardsUseCase,
                                FindCreditCardUseCase findCardUseCase, ChangeCreditLimitUseCase changeLimitUseCase,
                                CloseCreditCardUseCase closeUseCase, GetCreditCardBalanceUseCase balanceUseCase,
                                ChargeCreditCardUseCase chargeUseCase, PayCreditCardUseCase payUseCase,
                                GetPaymentInfoUseCase paymentInfoUseCase, CreditRestMapper mapper) {
        this.issueUseCase = issueUseCase;
        this.findCardsUseCase = findCardsUseCase;
        this.findCardUseCase = findCardUseCase;
        this.changeLimitUseCase = changeLimitUseCase;
        this.closeUseCase = closeUseCase;
        this.balanceUseCase = balanceUseCase;
        this.chargeUseCase = chargeUseCase;
        this.payUseCase = payUseCase;
        this.paymentInfoUseCase = paymentInfoUseCase;
        this.mapper = mapper;
    }

    @Override
    public Mono<ResponseEntity<Flux<CreditCard>>> listCreditCards(String customerId, CardStatus status,
                                                                  ServerWebExchange exchange) {
        Flux<CreditCard> cards = RxJavaReactorBridge
                .toFlux(findCardsUseCase.execute(mapper.toFilter(customerId, status)))
                .map(mapper::toDto);
        return Mono.just(ResponseEntity.ok(cards));
    }

    @Override
    public Mono<ResponseEntity<CreditCard>> issueCreditCard(Mono<IssueCreditCardRequest> request,
                                                            ServerWebExchange exchange) {
        return request
                .map(mapper::toCommand)
                .flatMap(command -> RxJavaReactorBridge.toMono(issueUseCase.execute(command)))
                .map(card -> ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(card)));
    }

    @Override
    public Mono<ResponseEntity<CreditCard>> getCreditCard(String id, ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(findCardUseCase.execute(new CreditCardId(id)))
                .map(card -> ResponseEntity.ok(mapper.toDto(card)));
    }

    @Override
    public Mono<ResponseEntity<CreditCard>> changeCreditCardLimit(String id, Mono<ChangeLimitRequest> request,
                                                                  ServerWebExchange exchange) {
        return request
                .map(body -> mapper.toCommand(id, body))
                .flatMap(command -> RxJavaReactorBridge.toMono(changeLimitUseCase.execute(command)))
                .map(card -> ResponseEntity.ok(mapper.toDto(card)));
    }

    @Override
    public Mono<ResponseEntity<Void>> closeCreditCard(String id, ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(closeUseCase.execute(new CreditCardId(id)))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @Override
    public Mono<ResponseEntity<CardBalance>> getCreditCardBalance(String id, ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(balanceUseCase.execute(new CreditCardId(id)))
                .map(view -> ResponseEntity.ok(mapper.toDto(view)));
    }

    @Override
    public Mono<ResponseEntity<ChargeResult>> chargeCreditCard(String id, Mono<ChargeRequest> request,
                                                               ServerWebExchange exchange) {
        return request
                .map(body -> mapper.toCommand(id, body))
                .flatMap(command -> RxJavaReactorBridge.toMono(chargeUseCase.execute(command)))
                .map(result -> ResponseEntity.ok(mapper.toDto(result)));
    }

    @Override
    public Mono<ResponseEntity<PaymentResult>> payCreditCard(String id, Mono<PaymentRequest> request,
                                                             ServerWebExchange exchange) {
        return request
                .map(body -> mapper.toCommand(id, body))
                .flatMap(command -> RxJavaReactorBridge.toMono(payUseCase.execute(command)))
                .map(result -> ResponseEntity.ok(mapper.toDto(result)));
    }

    @Override
    public Mono<ResponseEntity<PaymentInfo>> getCreditCardPaymentInfo(String id, ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(paymentInfoUseCase.execute(ProductType.CREDIT_CARD, id))
                .map(view -> ResponseEntity.ok(mapper.toDto(view)));
    }
}
