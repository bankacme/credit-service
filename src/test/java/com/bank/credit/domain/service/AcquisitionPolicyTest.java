package com.bank.credit.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CustomerNotFoundException;
import com.bank.credit.domain.model.CustomerSnapshot;
import com.bank.credit.domain.model.CustomerType;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.domain.model.ProductType;
import com.bank.credit.domain.service.validation.AcquisitionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

class AcquisitionPolicyTest {

    private final AcquisitionPolicy policy = new AcquisitionPolicy();

    private static CustomerSnapshot customer(CustomerType type, String status) {
        return new CustomerSnapshot("cust-1", type, status);
    }

    private static String codeOf(Throwable e) {
        return ((BusinessRuleViolationException) e).getErrorCode();
    }

    @ParameterizedTest
    @EnumSource(ProductType.class)
    void anUnknownCustomerIsNotFound(ProductType product) {
        AcquisitionContext context = new AcquisitionContext(product, "missing", null, false, false);

        assertThatThrownBy(() -> policy.evaluate(context)).isInstanceOf(CustomerNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(ProductType.class)
    void anInactiveCustomerCannotAcquireAnything(ProductType product) {
        AcquisitionContext context = new AcquisitionContext(product, "cust-1",
                customer(CustomerType.PERSONAL, "INACTIVE"), false, false);

        assertThatThrownBy(() -> policy.evaluate(context))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("CUSTOMER_INACTIVE"));
    }

    @ParameterizedTest(name = "{0} pidiendo {1} con un crédito no pagado → {2}")
    @CsvSource({
        "PERSONAL, CREDIT,      PERSONAL_CREDIT_LIMIT_REACHED",
        "PERSONAL, CREDIT_CARD, OK",
        "BUSINESS, CREDIT,      OK",
        "BUSINESS, CREDIT_CARD, OK"
    })
    void onlyAPersonalCustomerIsLimitedToOneUnpaidCredit(CustomerType type, ProductType product, String expected) {
        AcquisitionContext context = new AcquisitionContext(product, "cust-1", customer(type, "ACTIVE"), true,
                false);

        if ("OK".equals(expected)) {
            assertThat(policy.evaluate(context)).isEqualTo(OwnerType.of(type));
        } else {
            assertThatThrownBy(() -> policy.evaluate(context))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(expected));
        }
    }

    @ParameterizedTest
    @EnumSource(ProductType.class)
    void overdueDebtBlocksAnyNewProduct(ProductType product) {
        AcquisitionContext context = new AcquisitionContext(product, "cust-1",
                customer(CustomerType.BUSINESS, "ACTIVE"), false, true);

        assertThatThrownBy(() -> policy.evaluate(context))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("OVERDUE_DEBT"));
    }

    @Test
    void theFirstFailingRuleWins() {
        // Inactivo, con crédito no pagado y deuda vencida: corta en el eslabón 2.
        AcquisitionContext context = new AcquisitionContext(ProductType.CREDIT, "cust-1",
                customer(CustomerType.PERSONAL, "INACTIVE"), true, true);

        assertThatThrownBy(() -> policy.evaluate(context))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("CUSTOMER_INACTIVE"));
    }

    @Test
    void theProductTypeFollowsTheCustomerType() {
        AcquisitionContext business = new AcquisitionContext(ProductType.CREDIT, "cust-1",
                customer(CustomerType.BUSINESS, "ACTIVE"), false, false);
        AcquisitionContext personal = new AcquisitionContext(ProductType.CREDIT, "cust-1",
                customer(CustomerType.PERSONAL, "ACTIVE"), false, false);

        assertThat(policy.evaluate(business)).isEqualTo(OwnerType.BUSINESS);
        assertThat(policy.evaluate(personal)).isEqualTo(OwnerType.PERSONAL);
    }
}
