package com.bank.credit.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.bank.credit.application.port.in.CheckOverdueUseCase;
import com.bank.credit.application.port.in.RecoverUnrecordedOperationsUseCase;
import com.bank.credit.application.view.OverdueCheckResult;
import com.bank.credit.application.view.RecoveryResult;
import com.bank.credit.infrastructure.fixture.CreditFixtures;
import com.bank.credit.infrastructure.mapper.CreditRestMapper;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/** POST /overdue-checks y POST /credit-recovery-runs. */
@WebFluxTest({OverdueCheckController.class, InternalController.class})
@Import(CreditRestMapper.class)
class OperationsControllersTest {

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private CheckOverdueUseCase checkOverdueUseCase;
    @MockitoBean
    private RecoverUnrecordedOperationsUseCase recoverUseCase;

    @Test
    void runsTheOverdueCheckForTheGivenDate() {
        LocalDate asOf = LocalDate.of(2026, 11, 10);
        given(checkOverdueUseCase.execute(eq(asOf)))
                .willReturn(Single.just(new OverdueCheckResult(asOf, CreditFixtures.CLOCK.instant(), 1, 0, 1)));

        client.post().uri("/api/v1/overdue-checks?asOf=2026-11-10").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.asOf").isEqualTo("2026-11-10")
                .jsonPath("$.creditsMarked").isEqualTo(1)
                .jsonPath("$.customersAffected").isEqualTo(1);
    }

    @Test
    void aMalformedAsOfIs400() {
        client.post().uri("/api/v1/overdue-checks?asOf=10-11-2026").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void runsTheRecoveryWithTheConfiguredDefault() {
        given(recoverUseCase.execute(isNull()))
                .willReturn(Single.just(new RecoveryResult(CreditFixtures.CLOCK.instant(), 2, 3, 2, 1)));

        client.post().uri("/api/v1/credit-recovery-runs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.found").isEqualTo(3)
                .jsonPath("$.stillPending").isEqualTo(1);
    }
}
