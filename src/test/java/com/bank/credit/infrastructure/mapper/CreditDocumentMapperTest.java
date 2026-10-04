package com.bank.credit.infrastructure.mapper;

import static com.bank.credit.infrastructure.fixture.CreditFixtures.CLOCK;
import static com.bank.credit.infrastructure.fixture.CreditFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.infrastructure.adapter.out.persistence.CreditDocument;
import com.bank.credit.infrastructure.fixture.CreditFixtures;
import org.junit.jupiter.api.Test;

class CreditDocumentMapperTest {

    private final CreditDocumentMapper mapper = new CreditDocumentMapper();

    @Test
    void roundTripsEveryField() {
        Credit credit = CreditFixtures.credit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));

        CreditDocument document = mapper.toDocument(credit);

        assertThat(document.getOwnerType()).isEqualTo("PERSONAL");
        assertThat(document.getDueDate()).isEqualTo(TODAY.plusDays(90));
        assertThat(mapper.toDomain(document)).isEqualTo(credit);
    }

    @Test
    void unpaidPersonalIsOnlySetForAnActiveOrOverduePersonalCredit() {
        Credit personal = CreditFixtures.credit("cust-A", OwnerType.PERSONAL, "100.00", TODAY.plusDays(10));
        Credit business = CreditFixtures.credit("cust-E", OwnerType.BUSINESS, "100.00", TODAY.plusDays(10));
        Credit overdue = personal.markOverdueIfDue(TODAY.plusDays(11), CLOCK).orElseThrow();
        Credit paid = personal.registerPayment(new OperationId("op-00001"), Money.of("100.00"), null, CLOCK)
                .credit();

        assertThat(mapper.toDocument(personal).getUnpaidPersonal()).isTrue();
        assertThat(mapper.toDocument(overdue).getUnpaidPersonal()).isTrue();
        assertThat(mapper.toDocument(business).getUnpaidPersonal()).isNull();
        assertThat(mapper.toDocument(paid).getUnpaidPersonal()).isNull();
        assertThat(mapper.toDocument(paid.close(CLOCK)).getUnpaidPersonal()).isNull();
    }
}
