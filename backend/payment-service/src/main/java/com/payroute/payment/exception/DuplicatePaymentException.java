package com.payroute.payment.exception;

public class DuplicatePaymentException extends RuntimeException {

    private final Long existingPaymentId;

    public DuplicatePaymentException(String message) {
        super(message);
        this.existingPaymentId = null;
    }

    public DuplicatePaymentException(String message, Long existingPaymentId) {
        super(message);
        this.existingPaymentId = existingPaymentId;
    }

    public Long getExistingPaymentId() {
        return existingPaymentId;
    }
}
