package com.bank.credit.infrastructure.mapper;

import static com.bank.credit.infrastructure.fixture.CreditFixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.domain.model.CardNumber;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.infrastructure.adapter.out.persistence.CreditCardDocument;
import com.bank.credit.infrastructure.fixture.CreditFixtures;
import org.junit.jupiter.api.Test;

class CreditCardDocumentMapperTest {

    private final CreditCardDocumentMapper mapper = new CreditCardDocumentMapper();

    @Test
    void roundTripsACardWithoutPaymentDueDate() {
        CreditCard card = CreditFixtures.card("cust-A", "2000.00");

        CreditCardDocument document = mapper.toDocument(card);

        assertThat(document.getPaymentDueDate()).isNull();
        assertThat(mapper.toDomain(document)).isEqualTo(card);
    }

    @Test
    void roundTripsAChargedCardAndNeverPrintsTheFullNumber() {
        CreditCard card = CreditFixtures.card("cust-A", "2000.00", new CardNumber("4000001234564821"))
                .charge(new OperationId("op-00001"), Money.of("500.00"), 30, CLOCK).card();

        CreditCardDocument document = mapper.toDocument(card);

        assertThat(document.getCardNumber()).isEqualTo("4000001234564821");
        assertThat(document.toString()).doesNotContain("4000001234564821");
        assertThat(mapper.toDomain(document)).isEqualTo(card);
    }
}
