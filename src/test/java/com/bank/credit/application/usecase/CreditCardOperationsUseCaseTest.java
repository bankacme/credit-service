package com.bank.credit.application.usecase;

import static com.bank.credit.application.usecase.CreditFixture.TERM_DAYS;
import static com.bank.credit.application.usecase.CreditFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.application.command.ChargeCommand;
import com.bank.credit.application.command.PaymentCommand;
import com.bank.credit.domain.event.ChargeRegistered;
import com.bank.credit.domain.event.CreditCardUpdated;
import com.bank.credit.domain.event.CustomerOverdueCleared;
import com.bank.credit.domain.event.PaymentRegistered;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.ChargeResult;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.PaymentResult;
import org.junit.jupiter.api.Test;

/** Consumo y pago de tarjeta (ChargeCreditCardUseCaseImpl y PayCreditCardUseCaseImpl). */
class CreditCardOperationsUseCaseTest {

    private final CreditFixture f = new CreditFixture();
    private final ChargeCreditCardUseCaseImpl charge = f.chargeCard();
    private final PayCreditCardUseCaseImpl pay = f.payCard();

    private ChargeCommand chargeOf(CreditCard card, String operationId, String amount) {
        return new ChargeCommand(card.id(), new OperationId(operationId), Money.of(amount), "Supermercado");
    }

    private PaymentCommand payOf(CreditCard card, String operationId, String amount) {
        return new PaymentCommand(card.id().value(), new OperationId(operationId), Money.of(amount), null);
    }

    private static boolean hasCode(Throwable e, String code) {
        return e instanceof BusinessRuleViolationException v && v.getErrorCode().equals(code);
    }

    @Test
    void aChargeUsesTheLineSetsTheDueDateRecordsItAndPublishes() {
        CreditCard card = f.givenCard("cust-A", "2000.00");

        ChargeResult result = charge.execute(chargeOf(card, "op-00001", "500.00")).blockingGet();

        assertThat(result.usedAmount()).isEqualTo(Money.of("500.00"));
        assertThat(result.availableCredit()).isEqualTo(Money.of("1500.00"));
        assertThat(result.paymentDueDate()).isEqualTo(TODAY.plusDays(TERM_DAYS));
        assertThat(f.operations.get("op-00001").recorded()).isTrue();
        assertThat(f.operations.get("op-00001").description()).isEqualTo("Supermercado");
        assertThat(f.events.ofType(CreditCardUpdated.class)).hasSize(1);
        assertThat(f.events.ofType(ChargeRegistered.class)).hasSize(1);
    }

    @Test
    void aChargeOverTheLineIsRejectedAndLeavesNoOperation() {
        CreditCard card = f.givenCard("cust-A", "2000.00");

        charge.execute(chargeOf(card, "op-00001", "2000.01")).test()
                .assertError(e -> hasCode(e, "CREDIT_LIMIT_EXCEEDED"));

        assertThat(f.operations.size()).isZero();
    }

    @Test
    void repeatingAChargeDoesNotChargeTwice() {
        CreditCard card = f.givenCard("cust-A", "2000.00");
        ChargeResult first = charge.execute(chargeOf(card, "op-00001", "500.00")).blockingGet();

        ChargeResult second = charge.execute(chargeOf(card, "op-00001", "500.00")).blockingGet();

        assertThat(second).isEqualTo(first);
        assertThat(f.cards.get(card.id()).usedAmount()).isEqualTo(Money.of("500.00"));
        charge.execute(chargeOf(card, "op-00001", "600.00")).test()
                .assertError(e -> hasCode(e, "OPERATION_ID_REUSED"));
    }

    @Test
    void anOperationIdUsedForAPaymentCannotBeReusedForACharge() {
        CreditCard card = f.givenCard("cust-A", "2000.00");
        charge.execute(chargeOf(card, "op-00001", "500.00")).test().assertComplete();

        pay.execute(payOf(card, "op-00001", "500.00")).test().assertError(e -> hasCode(e, "OPERATION_ID_REUSED"));
    }

    @Test
    void payingAnOverdueCardInFullReactivatesItAndPublishesCleared() {
        CreditCard card = f.givenCard("cust-A", "2000.00");
        charge.execute(chargeOf(card, "op-00001", "500.00")).test().assertComplete();
        f.checkOverdue().execute(TODAY.plusDays(TERM_DAYS + 1)).test().assertComplete();
        assertThat(f.cards.get(card.id()).status()).isEqualTo(CardStatus.OVERDUE);

        PaymentResult result = pay.execute(payOf(card, "op-00002", "500.00")).blockingGet();

        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.resultingBalance().isZero()).isTrue();
        assertThat(f.cards.get(card.id()).paymentDueDate()).isNull();
        assertThat(f.events.ofType(PaymentRegistered.class)).hasSize(1);
        assertThat(f.events.ofType(CustomerOverdueCleared.class)).hasSize(1);
    }

    @Test
    void payingMoreThanWhatIsUsedIsAnOverpayment() {
        CreditCard card = f.givenCard("cust-A", "2000.00");

        pay.execute(payOf(card, "op-00001", "1.00")).test().assertError(e -> hasCode(e, "OVERPAYMENT"));
    }
}
