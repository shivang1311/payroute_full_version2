package com.payroute.ledger.exception;

/**
 * Thrown when a caller is authenticated but not authorized to access a resource
 * — e.g. a CUSTOMER asking for another party's account ledger. Mapped to HTTP
 * 403 by {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
