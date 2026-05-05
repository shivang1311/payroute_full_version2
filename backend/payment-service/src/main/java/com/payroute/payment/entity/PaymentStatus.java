package com.payroute.payment.entity;

public enum PaymentStatus {
    INITIATED,
    VALIDATED,
    VALIDATION_FAILED,
    SCREENING,
    HELD,
    ROUTED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REVERSED
}
