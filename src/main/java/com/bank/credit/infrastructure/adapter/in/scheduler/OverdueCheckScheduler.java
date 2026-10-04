package com.bank.credit.infrastructure.adapter.in.scheduler;

import com.bank.credit.application.port.in.CheckOverdueUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada: dispara la revisión diaria de deuda vencida (credit.overdue.cron, 01:00 en
 * la zona del banco). Es el mismo caso de uso que POST /overdue-checks.
 */
@Component
@Slf4j
public class OverdueCheckScheduler {

    private final CheckOverdueUseCase checkOverdueUseCase;

    public OverdueCheckScheduler(CheckOverdueUseCase checkOverdueUseCase) {
        this.checkOverdueUseCase = checkOverdueUseCase;
    }

    @Scheduled(cron = "${credit.overdue.cron:0 0 1 * * *}", zone = "${bank.zone}")
    public void runDailyCheck() {
        checkOverdueUseCase.execute(null).subscribe(
                result -> log.info("Overdue check as of {}: credits={} cards={} customers={}", result.asOf(),
                        result.creditsMarked(), result.cardsMarked(), result.customersAffected()),
                error -> log.error("Overdue check failed", error));
    }
}
