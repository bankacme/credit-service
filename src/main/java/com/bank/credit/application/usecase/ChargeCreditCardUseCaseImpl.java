package com.bank.credit.application.usecase;

import com.bank.credit.application.command.ChargeCommand;
import com.bank.credit.application.port.in.ChargeCreditCardUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.OperationLogPort;
import com.bank.credit.application.port.out.UnitOfWorkPort;
import com.bank.credit.domain.event.ChargeRegistered;
import com.bank.credit.domain.event.CreditCardUpdated;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.model.ChargeResult;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.OperationType;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

/**
 * Consumo con control de línea (data-model 2.3), idempotente por operationId. Un rechazo
 * (CREDIT_LIMIT_EXCEEDED, INVALID_STATE) no deja registro: repetirlo se evalúa de nuevo.
 */
public class ChargeCreditCardUseCaseImpl implements ChargeCreditCardUseCase {

    private final CreditCardRepositoryPort cardRepositoryPort;
    private final OperationLogPort operationLogPort;
    private final UnitOfWorkPort unitOfWorkPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final HistoryRecorder historyRecorder;
    private final int paymentTermDays;
    private final Clock clock;

    public ChargeCreditCardUseCaseImpl(CreditCardRepositoryPort cardRepositoryPort,
                                       OperationLogPort operationLogPort, UnitOfWorkPort unitOfWorkPort,
                                       CreditEventPublisherPort eventPublisherPort, HistoryRecorder historyRecorder,
                                       int paymentTermDays, Clock clock) {
        this.cardRepositoryPort = cardRepositoryPort;
        this.operationLogPort = operationLogPort;
        this.unitOfWorkPort = unitOfWorkPort;
        this.eventPublisherPort = eventPublisherPort;
        this.historyRecorder = historyRecorder;
        this.paymentTermDays = paymentTermDays;
        this.clock = clock;
    }

    @Override
    public Single<ChargeResult> execute(ChargeCommand command) {
        return operationLogPort.find(command.operationId())
                .flatMapSingle(existing -> replay(existing, command))
                .switchIfEmpty(Single.defer(() -> chargeNew(command)));
    }

    private Single<ChargeResult> replay(CreditOperation existing, ChargeCommand command) {
        if (!existing.matches(command.cardId().value(), OperationType.CHARGE, command.amount())) {
            return Single.error(new BusinessRuleViolationException("OPERATION_ID_REUSED",
                    "operationId " + command.operationId().value() + " was already used for a different operation"));
        }
        return historyRecorder.recordQuietly(existing).andThen(Single.just(existing.toChargeResult()));
    }

    private Single<ChargeResult> chargeNew(ChargeCommand command) {
        return cardRepositoryPort.findById(command.cardId())
                .switchIfEmpty(Single.error(() -> new CreditCardNotFoundException(command.cardId().value())))
                .flatMap(card -> {
                    CreditCard.ChargeOutcome outcome = card.charge(command.operationId(), command.amount(),
                            paymentTermDays, clock);
                    CreditOperation operation = CreditOperation.ofCharge(outcome.result(), card.customerId(),
                            command.description(), clock.instant());
                    return unitOfWorkPort.saveCardAndOperation(outcome.card(), operation)
                            .flatMap(saved -> eventPublisherPort.publish(CreditCardUpdated.from(saved))
                                    .andThen(eventPublisherPort.publish(ChargeRegistered.from(operation)))
                                    .andThen(historyRecorder.recordQuietly(operation))
                                    .andThen(Single.just(outcome.result())));
                });
    }
}
