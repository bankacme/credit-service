package com.bank.credit.infrastructure.adapter.in.rest;

import com.bank.credit.application.port.in.RecoverUnrecordedOperationsUseCase;
import com.bank.credit.infrastructure.adapter.in.rest.api.InternalApi;
import com.bank.credit.infrastructure.adapter.in.rest.dto.RecoveryResult;
import com.bank.credit.infrastructure.mapper.CreditRestMapper;
import com.bank.credit.infrastructure.support.RxJavaReactorBridge;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** POST /credit-recovery-runs: reintenta registrar en el historial lo que quedó pendiente (P1/P2). */
@RestController
public class InternalController implements InternalApi {

    private final RecoverUnrecordedOperationsUseCase recoverUseCase;
    private final CreditRestMapper mapper;

    public InternalController(RecoverUnrecordedOperationsUseCase recoverUseCase, CreditRestMapper mapper) {
        this.recoverUseCase = recoverUseCase;
        this.mapper = mapper;
    }

    @Override
    public Mono<ResponseEntity<RecoveryResult>> runCreditRecovery(Integer olderThanMinutes,
                                                                  ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(recoverUseCase.execute(olderThanMinutes))
                .map(result -> ResponseEntity.ok(mapper.toDto(result)));
    }
}
