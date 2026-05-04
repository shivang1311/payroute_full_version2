package com.payroute.payment.entity;

/**
 * How the payer identified the beneficiary at initiation time.
 * Used for per-method daily limits (e.g. UPI capped at ₹1,00,000 per debtor account per day).
 */
public enum PaymentMethod {
    UPI,            // Beneficiary identified by VPA / UPI handle
    BANK_TRANSFER,  // Beneficiary identified by account number + IFSC/IBAN
    OTHER           // Default — staff-initiated, scheduled runs, internal flows
}
