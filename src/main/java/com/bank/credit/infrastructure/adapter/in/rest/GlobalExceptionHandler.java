package com.bank.credit.infrastructure.adapter.in.rest;

import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.exception.CreditNotFoundException;
import com.bank.credit.domain.exception.CustomerNotFoundException;
import com.bank.credit.domain.exception.DownstreamServiceUnavailableException;
import com.bank.credit.infrastructure.adapter.in.rest.dto.ErrorResponse;
import com.bank.credit.infrastructure.adapter.in.rest.dto.FieldError;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/**
 * Cuerpo de error estándar del contrato ({@code timestamp, status, code, message, path}). Reglas de
 * negocio → 422, salvo OPERATION_ID_REUSED y CONCURRENT_MODIFICATION → 409; no encontrados → 404;
 * customer-service o transaction-service sin responder → 503; formato → 400 VALIDATION_ERROR.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> CONFLICT_CODES = Set.of("CONCURRENT_MODIFICATION", "OPERATION_ID_REUSED");
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(CustomerNotFoundException ex,
                                                                ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(CreditNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCreditNotFound(CreditNotFoundException ex,
                                                              ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, "CREDIT_NOT_FOUND", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(CreditCardNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCardNotFound(CreditCardNotFoundException ex,
                                                            ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, "CREDIT_CARD_NOT_FOUND", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(DownstreamServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDownstreamUnavailable(DownstreamServiceUnavailableException ex,
                                                                     ServerWebExchange exchange) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleViolationException ex,
                                                            ServerWebExchange exchange) {
        HttpStatus status = CONFLICT_CODES.contains(ex.getErrorCode())
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return build(status, ex.getErrorCode(), ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(WebExchangeBindException ex,
                                                              ServerWebExchange exchange) {
        List<FieldError> details = ex.getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Validation failed", exchange, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex,
                                                               ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getReason(), exchange, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handlePathValidation(ConstraintViolationException ex,
                                                              ServerWebExchange exchange) {
        List<FieldError> details = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    return fieldError(path.substring(path.lastIndexOf('.') + 1), violation.getMessage());
                })
                .toList();
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Validation failed", exchange, details);
    }

    /** Parámetros con formato inválido (una fecha mal escrita en asOf, un enum desconocido) o cuerpo ilegible. */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableInput(ServerWebInputException ex,
                                                               ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getReason(), exchange, null);
    }

    /** Defensivo: una validación del dominio que el contrato ya debería haber atajado (p. ej. monto 0). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                               ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage(), exchange, null);
    }

    private static FieldError fieldError(String field, String message) {
        FieldError detail = new FieldError();
        detail.setField(field);
        detail.setMessage(message);
        return detail;
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                       ServerWebExchange exchange, List<FieldError> details) {
        ErrorResponse body = new ErrorResponse();
        body.setTimestamp(OffsetDateTime.now());
        body.setStatus(status.value());
        body.setCode(code);
        body.setMessage(message);
        body.setPath(exchange.getRequest().getPath().value());
        body.setDetails(details);
        return ResponseEntity.status(status).body(body);
    }
}
