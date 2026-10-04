package com.bank.credit.infrastructure.adapter.out.persistence;

import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CardNumberCollisionException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Traduce los errores de Mongo a los del dominio, en un solo lugar:
 * <ul>
 *   <li>conflicto de {@code @Version} → CONCURRENT_MODIFICATION (409; CheckOverdue lo reintenta);</li>
 *   <li>uk_credit_personal_unpaid → PERSONAL_CREDIT_LIMIT_REACHED (422, la carrera de la regla 3);</li>
 *   <li>uk_card_number → CardNumberCollisionException (el caso de uso genera otro número);</li>
 *   <li>_id repetido en credit_operations (dos peticiones con el mismo operationId a la vez) →
 *       CONCURRENT_MODIFICATION.</li>
 * </ul>
 */
final class PersistenceErrors {

    static final String PERSONAL_UNPAID_INDEX = "uk_credit_personal_unpaid";
    static final String CARD_NUMBER_INDEX = "uk_card_number";

    private PersistenceErrors() {
    }

    static Throwable translate(Throwable error) {
        if (error instanceof OptimisticLockingFailureException) {
            return concurrentModification();
        }
        if (error instanceof DuplicateKeyException) {
            String message = error.getMessage() == null ? "" : error.getMessage();
            if (message.contains(PERSONAL_UNPAID_INDEX)) {
                return new BusinessRuleViolationException("PERSONAL_CREDIT_LIMIT_REACHED",
                        "The customer already has an unpaid personal credit");
            }
            if (message.contains(CARD_NUMBER_INDEX)) {
                return new CardNumberCollisionException();
            }
            if (message.contains("credit_operations")) {
                return concurrentModification();
            }
        }
        return error;
    }

    private static BusinessRuleViolationException concurrentModification() {
        return new BusinessRuleViolationException("CONCURRENT_MODIFICATION",
                "The product was modified by another request; try again");
    }
}
