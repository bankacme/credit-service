package com.bank.credit.infrastructure.adapter.out.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.domain.exception.DownstreamServiceUnavailableException;
import com.bank.credit.domain.model.CustomerSnapshot;
import com.bank.credit.domain.model.CustomerType;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

class CustomerRestAdapterTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private CustomerRestAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CustomerRestAdapter(WebClient.builder(), wireMock.baseUrl(),
                ResilienceTestConfig.circuitBreakers(), ResilienceTestConfig.timeLimiters());
    }

    private TestObserver<CustomerSnapshot> find(String id) {
        return adapter.findById(id).test().awaitDone(5, TimeUnit.SECONDS);
    }

    @Test
    void mapsTheFieldsItNeedsAndIgnoresTheRest() {
        wireMock.stubFor(get(urlEqualTo("/customers/cust-E")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "id": "cust-E",
                          "type": "BUSINESS",
                          "profile": "PYME",
                          "name": "Bodega El Sol",
                          "status": "ACTIVE"
                        }
                        """)));

        find("cust-E").assertValue(new CustomerSnapshot("cust-E", CustomerType.BUSINESS, "ACTIVE"));
    }

    @Test
    void aFourOhFourIsEmptyNotAnError() {
        wireMock.stubFor(get(urlEqualTo("/customers/missing")).willReturn(aResponse().withStatus(404)));

        find("missing").assertNoErrors().assertComplete().assertNoValues();
    }

    @Test
    void aSlowResponseIsCutAtTwoSecondsAsServiceUnavailable() {
        wireMock.stubFor(get(urlEqualTo("/customers/slow")).willReturn(aResponse()
                .withFixedDelay(3000).withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"slow\",\"type\":\"PERSONAL\",\"status\":\"ACTIVE\"}")));

        long start = System.nanoTime();
        find("slow").assertError(DownstreamServiceUnavailableException.class);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(2900);
    }

    @Test
    void aServerErrorIsServiceUnavailable() {
        wireMock.stubFor(get(urlEqualTo("/customers/boom")).willReturn(aResponse().withStatus(500)));

        find("boom").assertError(DownstreamServiceUnavailableException.class);
    }

    @Test
    void afterRepeatedFailuresTheCircuitOpensAndStopsCalling() {
        wireMock.stubFor(get(urlPathMatching("/customers/.*")).willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 4; i++) {
            find("boom");
        }

        find("boom").assertError(e -> e instanceof DownstreamServiceUnavailableException
                && e.getCause() instanceof CallNotPermittedException);
        wireMock.verify(4, getRequestedFor(urlPathMatching("/customers/.*")));
    }
}
