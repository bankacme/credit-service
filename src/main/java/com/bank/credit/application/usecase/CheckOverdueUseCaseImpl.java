package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.CheckOverdueUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.application.view.OverdueCheckResult;
import com.bank.credit.domain.event.CreditCardUpdated;
import com.bank.credit.domain.event.CreditUpdated;
import com.bank.credit.domain.event.ProductBecameOverdue;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Revisión de deuda vencida (data-model 2.5). Solo toma productos ACTIVE con fecha vencida, así
 * que repetirla no cambia ni publica nada. Cada producto se procesa aislado (un error no detiene a
 * los demás) y ante un conflicto de versión se recarga y reevalúa, hasta 3 intentos.
 */
public class CheckOverdueUseCaseImpl implements CheckOverdueUseCase {

    static final int MAX_ATTEMPTS = 3;
    private static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

    private final CreditRepositoryPort creditRepositoryPort;
    private final CreditCardRepositoryPort cardRepositoryPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final Clock clock;

    public CheckOverdueUseCaseImpl(CreditRepositoryPort creditRepositoryPort,
                                   CreditCardRepositoryPort cardRepositoryPort,
                                   CreditEventPublisherPort eventPublisherPort, Clock clock) {
        this.creditRepositoryPort = creditRepositoryPort;
        this.cardRepositoryPort = cardRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.clock = clock;
    }

    @Override
    public Single<OverdueCheckResult> execute(LocalDate asOfOverride) {
        LocalDate asOf = asOfOverride != null ? asOfOverride : LocalDate.now(clock);
        Single<List<String>> markedCredits = creditRepositoryPort.findActiveDueBefore(asOf)
                .flatMapMaybe(credit -> markCredit(credit, asOf, MAX_ATTEMPTS).onErrorComplete())
                .toList();
        Single<List<String>> markedCards = cardRepositoryPort.findActiveDueBefore(asOf)
                .flatMapMaybe(card -> markCard(card, asOf, MAX_ATTEMPTS).onErrorComplete())
                .toList();
        return markedCredits.flatMap(credits -> markedCards.map(cards -> {
            Set<String> customers = new HashSet<>(credits);
            customers.addAll(cards);
            return new OverdueCheckResult(asOf, clock.instant(), credits.size(), cards.size(), customers.size());
        }));
    }

    /** customerId del crédito si quedó marcado; vacío si ya no aplicaba. */
    private Maybe<String> markCredit(Credit credit, LocalDate asOf, int attemptsLeft) {
        Optional<Credit> marked = credit.markOverdueIfDue(asOf, clock);
        if (marked.isEmpty()) {
            return Maybe.empty();
        }
        return creditRepositoryPort.save(marked.get())
                .flatMapMaybe(saved -> eventPublisherPort.publish(CreditUpdated.from(saved))
                        .andThen(eventPublisherPort.publish(ProductBecameOverdue.from(saved)))
                        .andThen(Maybe.just(saved.customerId())))
                .onErrorResumeNext(error -> isConflict(error) && attemptsLeft > 1
                        ? creditRepositoryPort.findById(credit.id())
                                .flatMap(reloaded -> markCredit(reloaded, asOf, attemptsLeft - 1))
                        : Maybe.error(error));
    }

    private Maybe<String> markCard(CreditCard card, LocalDate asOf, int attemptsLeft) {
        Optional<CreditCard> marked = card.markOverdueIfDue(asOf, clock);
        if (marked.isEmpty()) {
            return Maybe.empty();
        }
        return cardRepositoryPort.save(marked.get())
                .flatMapMaybe(saved -> eventPublisherPort.publish(CreditCardUpdated.from(saved))
                        .andThen(eventPublisherPort.publish(ProductBecameOverdue.from(saved)))
                        .andThen(Maybe.just(saved.customerId())))
                .onErrorResumeNext(error -> isConflict(error) && attemptsLeft > 1
                        ? cardRepositoryPort.findById(card.id())
                                .flatMap(reloaded -> markCard(reloaded, asOf, attemptsLeft - 1))
                        : Maybe.error(error));
    }

    private static boolean isConflict(Throwable error) {
        return error instanceof BusinessRuleViolationException violation
                && CONCURRENT_MODIFICATION.equals(violation.getErrorCode());
    }
}
