package com.bank.credit.infrastructure.adapter.out.rest;

import static com.bank.credit.infrastructure.fixture.CreditFixtures.CLOCK;
import static com.bank.credit.infrastructure.fixture.CreditFixtures.TODAY;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.domain.exception.DownstreamServiceUnavailableException;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.infrastructure.fixture.CreditFixtures;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

class MovementRecorderRestAdapterTest {

    private static final String RECORDS = "/transactions/records";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private MovementRecorderRestAdapter adapter;

    private final CreditOperation thirdPartyPayment = CreditFixtures.paymentOf(
            CreditFixtures.credit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90)),
            "op-00000001", "300.00", "cust-B");

    @BeforeEach
    void setUp() {
        adapter = new MovementRecorderRestAdapter(WebClient.builder(), wireMock.baseUrl(),
                ResilienceTestConfig.circuitBreakers(), ResilienceTestConfig.timeLimiters());
    }

    private TestObserver<Void> record(CreditOperation operation) {
        return adapter.record(operation).test().awaitDone(5, TimeUnit.SECONDS);
    }

    @Test
    void sendsACreditPaymentAsCreditPaymentWithThePayer() {
        wireMock.stubFor(post(urlEqualTo(RECORDS)).willReturn(aResponse().withStatus(201)));

        record(thirdPartyPayment).assertComplete();

        wireMock.verify(postRequestedFor(urlEqualTo(RECORDS))
                .withRequestBody(matchingJsonPath("$.operationId", equalTo("op-00000001")))
                .withRequestBody(matchingJsonPath("$.productType", equalTo("CREDIT")))
                .withRequestBody(matchingJsonPath("$.type", equalTo("CREDIT_PAYMENT")))
                .withRequestBody(matchingJsonPath("$.customerId", equalTo("cust-A")))
                .withRequestBody(matchingJsonPath("$.payerCustomerId", equalTo("cust-B")))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("300.0")))
                .withRequestBody(matchingJsonPath("$.resultingBalance", equalTo("4700.0")))
                .withRequestBody(matchingJsonPath("$.occurredAt")));
    }

    @Test
    void sendsACardChargeAsCardChargeAndAcceptsADuplicate() {
        wireMock.stubFor(post(urlEqualTo(RECORDS)).willReturn(aResponse().withStatus(200)));
        CreditCard card = CreditFixtures.card("cust-A", "2000.00");
        CreditOperation charge = CreditOperation.ofCharge(
                card.charge(new OperationId("op-00000002"), Money.of("500.00"), 30, CLOCK).result(),
                card.customerId(), "Supermercado", CLOCK.instant());

        record(charge).assertComplete();

        wireMock.verify(postRequestedFor(urlEqualTo(RECORDS))
                .withRequestBody(matchingJsonPath("$.productType", equalTo("CREDIT_CARD")))
                .withRequestBody(matchingJsonPath("$.type", equalTo("CARD_CHARGE")))
                .withRequestBody(matchingJsonPath("$.description", equalTo("Supermercado"))));
    }

    @Test
    void mapsACardPaymentToCardPayment() {
        CreditCard card = CreditFixtures.card("cust-A", "2000.00")
                .charge(new OperationId("op-00000003"), Money.of("100.00"), 30, CLOCK).card();
        CreditOperation payment = CreditOperation.ofPayment(card.registerPayment(new OperationId("op-00000004"),
                Money.of("100.00"), null, CLOCK).result(), card.customerId(), CLOCK.instant());

        assertThat(MovementRecorderRestAdapter.movementType(payment)).isEqualTo("CARD_PAYMENT");
    }

    @Test
    void aRejectionOrATimeoutIsAnErrorSoTheOperationStaysPending() {
        wireMock.stubFor(post(urlEqualTo(RECORDS)).willReturn(aResponse().withStatus(422)));
        record(thirdPartyPayment).assertError(DownstreamServiceUnavailableException.class);

        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo(RECORDS)).willReturn(aResponse().withStatus(201).withFixedDelay(3000)));
        record(thirdPartyPayment).assertError(DownstreamServiceUnavailableException.class);
    }
}
