package com.bank.credit.infrastructure.support;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.NoSuchElementException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Borde entre los puertos/casos de uso (RxJava 3) y los tipos de Reactor que usan los
 * controllers generados (R5) y el {@code TransactionalOperator} de MongoUnitOfWorkAdapter. Copia
 * literal del de account-service y transaction-service (solo cambia el paquete): ambos son
 * {@code org.reactivestreams.Publisher}, así que no hace falta ninguna dependencia extra.
 */
public final class RxJavaReactorBridge {

    private RxJavaReactorBridge() {
    }

    // ---- RxJava 3 -> Reactor (use case -> controller / transaction) ----

    public static <T> Mono<T> toMono(Single<T> single) {
        return Mono.from(single.toFlowable());
    }

    public static <T> Mono<T> toMono(Maybe<T> maybe) {
        return Mono.from(maybe.toFlowable());
    }

    public static Mono<Void> toMono(Completable completable) {
        return Mono.from(completable.<Void>toFlowable());
    }

    public static <T> Flux<T> toFlux(Observable<T> observable) {
        return Flux.from(observable.toFlowable(BackpressureStrategy.BUFFER));
    }

    public static <T> Flux<T> toFlux(Flowable<T> flowable) {
        return Flux.from(flowable);
    }

    // ---- Reactor -> RxJava 3 (repository/transaction -> use case) ----

    public static <T> Single<T> toSingle(Mono<T> mono) {
        Mono<T> nonEmpty = mono.switchIfEmpty(
                Mono.error(new NoSuchElementException("Mono completed empty; cannot become a Single")));
        return Single.fromPublisher(nonEmpty);
    }

    public static <T> Maybe<T> toMaybe(Mono<T> mono) {
        return Maybe.fromPublisher(mono);
    }

    public static Completable toCompletable(Mono<Void> mono) {
        return Completable.fromPublisher(mono);
    }

    public static <T> Observable<T> toObservable(Flux<T> flux) {
        return Observable.fromPublisher(flux);
    }

    public static <T> Flowable<T> toFlowable(Flux<T> flux) {
        return Flowable.fromPublisher(flux);
    }
}
