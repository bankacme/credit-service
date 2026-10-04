package com.bank.credit.infrastructure.adapter.in.rest;

import com.bank.credit.application.port.in.CheckOverdueUseCase;
import com.bank.credit.infrastructure.adapter.in.rest.api.OverdueDebtApi;
import com.bank.credit.infrastructure.adapter.in.rest.dto.OverdueCheckResult;
import com.bank.credit.infrastructure.mapper.CreditRestMapper;
import com.bank.credit.infrastructure.support.RxJavaReactorBridge;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class OverdueCheckController implements OverdueDebtApi {

    private final CheckOverdueUseCase checkOverdueUseCase;
    private final CreditRestMapper mapper;

    public OverdueCheckController(CheckOverdueUseCase checkOverdueUseCase, CreditRestMapper mapper) {
        this.checkOverdueUseCase = checkOverdueUseCase;
        this.mapper = mapper;
    }

    @Override
    public Mono<ResponseEntity<OverdueCheckResult>> runOverdueCheck(LocalDate asOf, ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(checkOverdueUseCase.execute(asOf))
                .map(result -> ResponseEntity.ok(mapper.toDto(result)));
    }
}
