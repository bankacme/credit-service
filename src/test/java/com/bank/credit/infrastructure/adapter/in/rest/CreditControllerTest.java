package com.bank.credit.infrastructure.adapter.in.rest;

import static com.bank.credit.infrastructure.fixture.CreditFixtures.CLOCK;
import static com.bank.credit.infrastructure.fixture.CreditFixtures.TODAY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.bank.credit.application.port.in.CloseCreditUseCase;
import com.bank.credit.application.port.in.FindCreditUseCase;
import com.bank.credit.application.port.in.FindCreditsUseCase;
import com.bank.credit.application.port.in.GetPaymentInfoUseCase;
import com.bank.credit.application.port.in.OpenCreditUseCase;
import com.bank.credit.application.port.in.PayCreditUseCase;
import com.bank.credit.application.port.in.RescheduleCreditUseCase;
import com.bank.credit.application.view.PaymentInfoView;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CreditNotFoundException;
import com.bank.credit.domain.exception.CustomerNotFoundException;
import com.bank.credit.domain.exception.DownstreamServiceUnavailableException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditId;
import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.domain.model.ProductType;
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

@WebFluxTest(CreditController.class)
@Import(CreditRestMapper.class)
class CreditControllerTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private OpenCreditUseCase openCreditUseCase;
    @MockitoBean
    private FindCreditsUseCase findCreditsUseCase;
    @MockitoBean
    private FindCreditUseCase findCreditUseCase;
    @MockitoBean
    private RescheduleCreditUseCase rescheduleCreditUseCase;
    @MockitoBean
    private CloseCreditUseCase closeCreditUseCase;
    @MockitoBean
    private PayCreditUseCase payCreditUseCase;
    @MockitoBean
    private GetPaymentInfoUseCase getPaymentInfoUseCase;

    private final Credit credit = CreditFixtures.credit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));

    private WebTestClient.ResponseSpec post(String uri, String json) {
        return client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).bodyValue(json).exchange();
    }

    @Test
    void openCreditReturns201WithTheCredit() {
        given(openCreditUseCase.execute(argThat(c -> c != null && c.customerId().equals("cust-A")
                && c.amount().equals(Money.of("5000.00")) && c.dueDate().equals(TODAY.plusDays(90)))))
                .willReturn(Single.just(credit));

        post("/api/v1/credits", """
                { "customerId": "cust-A", "amount": 5000.00, "dueDate": "2026-12-30" }
                """)
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(credit.id().value())
                .jsonPath("$.ownerType").isEqualTo("PERSONAL")
                .jsonPath("$.outstandingBalance").isEqualTo(5000.0)
                .jsonPath("$.dueDate").isEqualTo("2026-12-30")
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void openCreditWithoutAmountIs400() {
        post("/api/v1/credits", """
                { "customerId": "cust-A", "dueDate": "2026-12-30" }
                """)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.details[0].field").isEqualTo("amount");
    }

    @Test
    void businessRulesMapTo422And404And503() {
        given(openCreditUseCase.execute(argThat(c -> c != null && c.customerId().equals("cust-A"))))
                .willReturn(Single.error(new BusinessRuleViolationException("PERSONAL_CREDIT_LIMIT_REACHED", "x")));
        given(openCreditUseCase.execute(argThat(c -> c != null && c.customerId().equals("nobody"))))
                .willReturn(Single.error(new CustomerNotFoundException("nobody")));
        given(openCreditUseCase.execute(argThat(c -> c != null && c.customerId().equals("cust-Z"))))
                .willReturn(Single.error(new DownstreamServiceUnavailableException("customer-service", null)));

        post("/api/v1/credits", """
                { "customerId": "cust-A", "amount": 1.00, "dueDate": "2026-12-30" }
                """)
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("PERSONAL_CREDIT_LIMIT_REACHED")
                .jsonPath("$.path").isEqualTo("/api/v1/credits");
        post("/api/v1/credits", """
                { "customerId": "nobody", "amount": 1.00, "dueDate": "2026-12-30" }
                """)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("CUSTOMER_NOT_FOUND");
        post("/api/v1/credits", """
                { "customerId": "cust-Z", "amount": 1.00, "dueDate": "2026-12-30" }
                """)
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void listCreditsPassesTheFilters() {
        given(findCreditsUseCase.execute(argThat(f -> f != null && "cust-A".equals(f.customerId())
                && f.ownerType() == OwnerType.PERSONAL && f.status() == CreditStatus.ACTIVE)))
                .willReturn(Flowable.just(credit));

        client.get().uri("/api/v1/credits?customerId=cust-A&ownerType=PERSONAL&status=ACTIVE").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void anUnknownStatusFilterIs400() {
        client.get().uri("/api/v1/credits?status=WHATEVER").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void getCreditReturns200Or404() {
        given(findCreditUseCase.execute(credit.id())).willReturn(Single.just(credit));
        given(findCreditUseCase.execute(new CreditId("missing")))
                .willReturn(Single.error(new CreditNotFoundException("missing")));

        client.get().uri("/api/v1/credits/{id}", credit.id().value()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.customerId").isEqualTo("cust-A");
        client.get().uri("/api/v1/credits/missing").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("CREDIT_NOT_FOUND");
    }

    @Test
    void rescheduleReturnsTheUpdatedCredit() {
        Credit rescheduled = credit.reschedule(TODAY.plusDays(120), false, CLOCK);
        given(rescheduleCreditUseCase.execute(any())).willReturn(Single.just(rescheduled));

        client.put().uri("/api/v1/credits/{id}", credit.id().value()).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        { "dueDate": "2027-01-29" }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.dueDate").isEqualTo("2027-01-29");
    }

    @Test
    void closeReturns204OrNotClosable() {
        given(closeCreditUseCase.execute(credit.id())).willReturn(Completable.complete());
        given(closeCreditUseCase.execute(new CreditId("with-balance")))
                .willReturn(Completable.error(new BusinessRuleViolationException("NOT_CLOSABLE", "x")));

        client.delete().uri("/api/v1/credits/{id}", credit.id().value()).exchange()
                .expectStatus().isNoContent();
        client.delete().uri("/api/v1/credits/with-balance").exchange()
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("NOT_CLOSABLE");
    }

    @Test
    void payCreditReturns200WithTheThirdPartyPayer() {
        given(payCreditUseCase.execute(argThat(c -> c != null && c.productId().equals(credit.id().value())
                && c.operationId().equals(new OperationId("op-00000001")) && "cust-B".equals(c.payerCustomerId()))))
                .willReturn(Single.just(credit.registerPayment(new OperationId("op-00000001"), Money.of("300.00"),
                        "cust-B", CLOCK).result()));

        post("/api/v1/credits/" + credit.id().value() + "/payments", """
                { "operationId": "op-00000001", "amount": 300.00, "payerCustomerId": "cust-B" }
                """)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productType").isEqualTo("CREDIT")
                .jsonPath("$.resultingBalance").isEqualTo(4700.0)
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.payerCustomerId").isEqualTo("cust-B");
    }

    @Test
    void reusingAnOperationIdIs409AndAShortOneIs400() {
        given(payCreditUseCase.execute(any()))
                .willReturn(Single.error(new BusinessRuleViolationException("OPERATION_ID_REUSED", "x")));

        post("/api/v1/credits/c-1/payments", """
                { "operationId": "op-00000001", "amount": 1.00 }
                """)
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("OPERATION_ID_REUSED");
        post("/api/v1/credits/c-1/payments", """
                { "operationId": "op-1", "amount": 1.00 }
                """)
                .expectStatus().isBadRequest();
    }

    @Test
    void paymentInfoShowsWhatIsOwed() {
        given(getPaymentInfoUseCase.execute(eq(ProductType.CREDIT), eq(credit.id().value())))
                .willReturn(Single.just(PaymentInfoView.of(credit)));

        client.get().uri("/api/v1/credits/{id}/payment-info", credit.id().value()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amountDue").isEqualTo(5000.0)
                .jsonPath("$.maskedNumber").doesNotExist();
    }
}
