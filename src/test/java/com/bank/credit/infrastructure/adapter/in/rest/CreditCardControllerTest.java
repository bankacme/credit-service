package com.bank.credit.infrastructure.adapter.in.rest;

import static com.bank.credit.infrastructure.fixture.CreditFixtures.CLOCK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

import com.bank.credit.application.port.in.ChangeCreditLimitUseCase;
import com.bank.credit.application.port.in.ChargeCreditCardUseCase;
import com.bank.credit.application.port.in.CloseCreditCardUseCase;
import com.bank.credit.application.port.in.FindCreditCardUseCase;
import com.bank.credit.application.port.in.FindCreditCardsUseCase;
import com.bank.credit.application.port.in.GetCreditCardBalanceUseCase;
import com.bank.credit.application.port.in.GetPaymentInfoUseCase;
import com.bank.credit.application.port.in.IssueCreditCardUseCase;
import com.bank.credit.application.port.in.PayCreditCardUseCase;
import com.bank.credit.application.view.CardBalanceView;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.model.CardNumber;
import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.infrastructure.fixture.CreditFixtures;
import com.bank.credit.infrastructure.mapper.CreditRestMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(CreditCardController.class)
@Import(CreditRestMapper.class)
class CreditCardControllerTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private IssueCreditCardUseCase issueUseCase;
    @MockitoBean
    private FindCreditCardsUseCase findCardsUseCase;
    @MockitoBean
    private FindCreditCardUseCase findCardUseCase;
    @MockitoBean
    private ChangeCreditLimitUseCase changeLimitUseCase;
    @MockitoBean
    private CloseCreditCardUseCase closeUseCase;
    @MockitoBean
    private GetCreditCardBalanceUseCase balanceUseCase;
    @MockitoBean
    private ChargeCreditCardUseCase chargeUseCase;
    @MockitoBean
    private PayCreditCardUseCase payUseCase;
    @MockitoBean
    private GetPaymentInfoUseCase paymentInfoUseCase;

    private final CreditCard card = CreditFixtures.card("cust-A", "2000.00", new CardNumber("4000001234564821"));

    private WebTestClient.ResponseSpec post(String uri, String json) {
        return client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).bodyValue(json).exchange();
    }

    @Test
    void issueReturns201AndNeverTheFullNumber() {
        given(issueUseCase.execute(argThat(c -> c != null && c.creditLimit().equals(Money.of("2000.00")))))
                .willReturn(Single.just(card));

        post("/api/v1/credit-cards", """
                { "customerId": "cust-A", "creditLimit": 2000.00 }
                """)
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.maskedNumber").isEqualTo("**** 4821")
                .jsonPath("$.availableCredit").isEqualTo(2000.0)
                .jsonPath("$.cardNumber").doesNotExist()
                .consumeWith(result -> org.assertj.core.api.Assertions
                        .assertThat(new String(result.getResponseBodyContent())).doesNotContain("4000001234564821"));
    }

    @Test
    void listAndGetCards() {
        given(findCardsUseCase.execute(argThat(f -> f != null && "cust-A".equals(f.customerId())
                && f.status() == CardStatus.ACTIVE)))
                .willReturn(Flowable.just(card));
        given(findCardUseCase.execute(new CreditCardId("missing")))
                .willReturn(Single.error(new CreditCardNotFoundException("missing")));

        client.get().uri("/api/v1/credit-cards?customerId=cust-A&status=ACTIVE").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].status").isEqualTo("ACTIVE");
        client.get().uri("/api/v1/credit-cards/missing").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("CREDIT_CARD_NOT_FOUND");
    }

    @Test
    void chargeReturnsTheResultOr422WhenOverTheLine() {
        given(chargeUseCase.execute(argThat(c -> c != null && c.amount().equals(Money.of("500.00")))))
                .willReturn(Single.just(card.charge(new OperationId("op-00000001"), Money.of("500.00"), 30, CLOCK)
                        .result()));
        given(chargeUseCase.execute(argThat(c -> c != null && c.amount().equals(Money.of("5000.00")))))
                .willReturn(Single.error(new BusinessRuleViolationException("CREDIT_LIMIT_EXCEEDED", "x")));

        post("/api/v1/credit-cards/" + card.id().value() + "/charges", """
                { "operationId": "op-00000001", "amount": 500.00, "description": "Supermercado" }
                """)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.usedAmount").isEqualTo(500.0)
                .jsonPath("$.availableCredit").isEqualTo(1500.0)
                .jsonPath("$.paymentDueDate").isEqualTo("2026-10-31");
        post("/api/v1/credit-cards/" + card.id().value() + "/charges", """
                { "operationId": "op-00000002", "amount": 5000.00 }
                """)
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("CREDIT_LIMIT_EXCEEDED");
    }

    @Test
    void payChangeLimitCloseAndBalance() {
        CreditCard charged = card.charge(new OperationId("op-00000001"), Money.of("500.00"), 30, CLOCK).card();
        given(payUseCase.execute(any())).willReturn(Single.just(
                charged.registerPayment(new OperationId("op-00000002"), Money.of("500.00"), null, CLOCK).result()));
        given(changeLimitUseCase.execute(any())).willReturn(Single.just(card.changeLimit(Money.of("3000.00"), CLOCK)));
        given(closeUseCase.execute(card.id())).willReturn(Completable.complete());
        given(balanceUseCase.execute(card.id())).willReturn(Single.just(CardBalanceView.of(charged, CLOCK.instant())));

        post("/api/v1/credit-cards/" + card.id().value() + "/payments", """
                { "operationId": "op-00000002", "amount": 500.00 }
                """)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productType").isEqualTo("CREDIT_CARD")
                .jsonPath("$.status").isEqualTo("ACTIVE");
        client.put().uri("/api/v1/credit-cards/{id}", card.id().value()).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        { "creditLimit": 3000.00 }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.creditLimit").isEqualTo(3000.0);
        client.delete().uri("/api/v1/credit-cards/{id}", card.id().value()).exchange().expectStatus().isNoContent();
        client.get().uri("/api/v1/credit-cards/{id}/balance", card.id().value()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.availableCredit").isEqualTo(1500.0)
                .jsonPath("$.asOf").exists();
    }
}
