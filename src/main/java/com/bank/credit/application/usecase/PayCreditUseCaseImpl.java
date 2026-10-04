package com.bank.credit.application.usecase;

import com.bank.credit.application.command.PaymentCommand;
import com.bank.credit.application.port.in.PayCreditUseCase;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.application.port.out.OperationLogPort;
import com.bank.credit.application.port.out.UnitOfWorkPort;
import com.bank.credit.domain.event.CreditUpdated;
import com.bank.credit.domain.event.PaymentRegistered;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CreditNotFoundException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditId;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.OperationType;
import com.bank.credit.domain.model.PaymentResult;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

/**
 * Pago de un crédito, propio o de tercero (data-model 2.2):
 * <ol>
 *   <li>Idempotencia: una operación ya guardada se devuelve tal cual (y si no se registró en el
 *       historial, se reintenta); con otros datos → 409 OPERATION_ID_REUSED.</li>
 *   <li>El dominio aplica el pago; crédito y operación se guardan juntos (UnitOfWorkPort).</li>
 *   <li>Publica credit.updated y credit.payment.registered; si salió de OVERDUE, revisa la limpieza.</li>
 *   <li>Registra en el historial sin que un fallo cambie la respuesta (HistoryRecorder).</li>
 * </ol>
 */
public class PayCreditUseCaseImpl implements PayCreditUseCase {

    private final CreditRepositoryPort creditRepositoryPort;
    private final OperationLogPort operationLogPort;
    private final UnitOfWorkPort unitOfWorkPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final OverdueClearance overdueClearance;
    private final HistoryRecorder historyRecorder;
    private final Clock clock;

    public PayCreditUseCaseImpl(CreditRepositoryPort creditRepositoryPort, OperationLogPort operationLogPort,
                                UnitOfWorkPort unitOfWorkPort, CreditEventPublisherPort eventPublisherPort,
                                OverdueClearance overdueClearance, HistoryRecorder historyRecorder, Clock clock) {
        this.creditRepositoryPort = creditRepositoryPort;
        this.operationLogPort = operationLogPort;
        this.unitOfWorkPort = unitOfWorkPort;
        this.eventPublisherPort = eventPublisherPort;
        this.overdueClearance = overdueClearance;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    @Override
    public Single<PaymentResult> execute(PaymentCommand command) {
        return operationLogPort.find(command.operationId())
                .flatMapSingle(existing -> replay(existing, command))
                .switchIfEmpty(Single.defer(() -> payNew(command)));
    }

    private Single<PaymentResult> replay(CreditOperation existing, PaymentCommand command) {
        if (!existing.matches(command.productId(), OperationType.PAYMENT, command.amount())) {
            return Single.error(new BusinessRuleViolationException("OPERATION_ID_REUSED",
                    "operationId " + command.operationId().value() + " was already used for a different operation"));
        }
        return historyRecorder.recordQuietly(existing).andThen(Single.just(existing.toPaymentResult()));
    }

    private Single<PaymentResult> payNew(PaymentCommand command) {
        return creditRepositoryPort.findById(new CreditId(command.productId()))
                .switchIfEmpty(Single.error(() -> new CreditNotFoundException(command.productId())))
                .flatMap(credit -> {
                    Credit.PaymentOutcome outcome = credit.registerPayment(command.operationId(), command.amount(),
                            command.payerCustomerId(), clock);
                    CreditOperation operation = CreditOperation.ofPayment(outcome.result(), credit.customerId(),
                            clock.instant());
                    return unitOfWorkPort.saveCreditAndOperation(outcome.credit(), operation)
                            .flatMap(saved -> eventPublisherPort.publish(CreditUpdated.from(saved))
                                    .andThen(eventPublisherPort.publish(PaymentRegistered.from(operation)))
                                    .andThen(outcome.leftOverdue()
                                            ? overdueClearance.afterLeavingOverdue(saved.customerId())
                                            : Completable.complete())
                                    .andThen(historyRecorder.recordQuietly(operation))
                                    .andThen(Single.just(outcome.result())));
                });
    }
}
