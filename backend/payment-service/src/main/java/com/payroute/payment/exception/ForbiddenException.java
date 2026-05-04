package com.payroute.payment.exception;

/**
 * Thrown when an authenticated caller is not permitted to perform an action
 * (e.g. CUSTOMER attempting to debit an account they do not own).
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
