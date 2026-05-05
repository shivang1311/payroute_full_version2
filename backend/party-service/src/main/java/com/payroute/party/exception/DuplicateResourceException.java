package com.payroute.party.exception;

/**
 * Thrown when a unique constraint would be violated — e.g. registering a
 * party with an email that already exists, or creating an account with a
 * duplicate (accountNumber, ifsc) pair. Mapped to HTTP 409 Conflict by
 * {@link GlobalExceptionHandler}.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
