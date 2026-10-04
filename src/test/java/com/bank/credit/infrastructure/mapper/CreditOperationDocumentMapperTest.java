package com.bank.credit.infrastructure.mapper;

import static com.bank.credit.infrastructure.fixture.CreditFixtures.CLOCK;
import static com.bank.credit.infrastructure.fixture.CreditFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.infrastructure.adapter.out.persistence.CreditOperationDocument;
import com.bank.credit.infrastructure.fixture.CreditFixtures;
import org.junit.jupiter.api.Test;

class CreditOperationDocumentMapperTest {

    private final CreditOperationDocumentMapper mapper = new CreditOperationDocumentMapper();

    @Test
    void roundTripsAThirdPartyPayment() {
        CreditOperation payment = CreditFixtures.paymentOf(
                CreditFixtures.credit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90)),
                "op-00001", "300.00", "cust-B");

        CreditOperationDocument document = mapper.toDocument(payment);

        assertThat(document.getId()).isEqualTo("op-00001");
        assertThat(document.getPayerCustomerId()).isEqualTo("cust-B");
        assertThat(document.getAvailableCredit()).isNull();
        assertThat(mapper.toDomain(document)).isEqualTo(payment);
    }

    @Test
    void roundTripsARecordedCharge() {
        CreditCard card = CreditFixtures.card("cust-A", "2000.00");
        CreditOperation charge = CreditOperation.ofCharge(
                card.charge(new OperationId("op-00002"), Money.of("500.00"), 30, CLOCK).result(),
                card.customerId(), "Supermercado", CLOCK.instant()).markRecorded(CLOCK.instant());

        CreditOperationDocument document = mapper.toDocument(charge);

        assertThat(document.isRecorded()).isTrue();
        assertThat(document.getPaymentDueDate()).isEqualTo(TODAY.plusDays(30));
        assertThat(mapper.toDomain(document)).isEqualTo(charge);
    }
}
