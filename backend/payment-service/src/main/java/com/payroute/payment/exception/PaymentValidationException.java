package com.payroute.payment.exception;

import java.util.List;

public class PaymentValidationException extends RuntimeException {

    private final List<String> errors;

    public PaymentValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public PaymentValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
