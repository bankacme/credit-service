package com.bank.credit.domain.exception;

/** customer-service o transaction-service no respondió a tiempo o el circuito está abierto (503). */
public class DownstreamServiceUnavailableException extends RuntimeException {

    public DownstreamServiceUnavailableException(String serviceName, Throwable cause) {
        super(serviceName + " did not respond in time", cause);
    }
}
