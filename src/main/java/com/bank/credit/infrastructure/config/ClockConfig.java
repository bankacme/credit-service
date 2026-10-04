package com.bank.credit.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Un único Clock en la zona del banco (bank.zone, America/Lima): define qué es "hoy". */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${bank.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
