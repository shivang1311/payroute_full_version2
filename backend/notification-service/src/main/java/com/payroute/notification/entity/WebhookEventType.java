package com.payroute.notification.entity;

public enum WebhookEventType {
    PAYMENT_INITIATED,
    PAYMENT_VALIDATED,
    PAYMENT_HELD,
    PAYMENT_ROUTED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_REVERSED
}
