package com.bank.credit.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * The OpenAPI generator does not emit the server prefix ({@code /api/v1}) in the generated interfaces,
 * so it is added here to every {@code @RestController}. Actuator endpoints are not affected.
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

    private static final String API_PREFIX = "/api/v1";

    @Override
    public void configurePathMatching(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forAnnotation(RestController.class));
    }
}
