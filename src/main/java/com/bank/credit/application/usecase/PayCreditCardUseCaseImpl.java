package com.bank.credit.application.usecase;

import com.bank.credit.application.command.PaymentCommand;
import com.bank.credit.application.port.in.PayCreditCardUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.OperationLogPort;
import com.bank.credit.application.port.out.UnitOfWorkPort;
import com.bank.credit.domain.event.CreditCardUpdated;
import com.bank.credit.domain.event.PaymentRegistered;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.OperationType;
import com.bank.credit.domain.model.PaymentResult;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

/** Pago de una tarjeta: mismo flujo que {@link PayCreditUseCaseImpl}, sobre CreditCard. */
public class PayCreditCardUseCaseImpl implements PayCreditCardUseCase {

    private final CreditCardRepositoryPort cardRepositoryPort;
    private final OperationLogPort operationLogPort;
    private final UnitOfWorkPort unitOfWorkPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final OverdueClearance overdueClearance;
    private final HistoryRecorder historyRecorder;
    private final Clock clock;

    public PayCreditCardUseCaseImpl(CreditCardRepositoryPort cardRepositoryPort, OperationLogPort operationLogPort,
                                    UnitOfWorkPort unitOfWorkPort, CreditEventPublisherPort eventPublisherPort,
                                    OverdueClearance overdueClearance, HistoryRecorder historyRecorder,
                                    Clock clock) {
        this.cardRepositoryPort = cardRepositoryPort;
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
        return cardRepositoryPort.findById(new CreditCardId(command.productId()))
                .switchIfEmpty(Single.error(() -> new CreditCardNotFoundException(command.productId())))
                .flatMap(card -> {
                    CreditCard.PaymentOutcome outcome = card.registerPayment(command.operationId(), command.amount(),
                            command.payerCustomerId(), clock);
                    CreditOperation operation = CreditOperation.ofPayment(outcome.result(), card.customerId(),
                            clock.instant());
                    return unitOfWorkPort.saveCardAndOperation(outcome.card(), operation)
                            .flatMap(saved -> eventPublisherPort.publish(CreditCardUpdated.from(saved))
                                    .andThen(eventPublisherPort.publish(PaymentRegistered.from(operation)))
                                    .andThen(outcome.leftOverdue()
                                            ? overdueClearance.afterLeavingOverdue(saved.customerId())
                                            : Completable.complete())
                                    .andThen(historyRecorder.recordQuietly(operation))
                                    .andThen(Single.just(outcome.result())));
                });
    }
}
