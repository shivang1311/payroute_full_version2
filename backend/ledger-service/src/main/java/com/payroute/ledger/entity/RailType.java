package com.payroute.ledger.entity;

/**
 * Payment rail / network. Drives fee selection and routing.
 */
public enum RailType {
    /** Internal book transfer between accounts in the same bank — no external network. */
    BOOK,
    /** National Electronic Funds Transfer (India). */
    NEFT,
    /** Real-Time Gross Settlement (India) — high-value, immediate settlement. */
    RTGS,
    /** Immediate Payment Service (India) — instant, 24x7. */
    IMPS,
    /** Automated Clearing House (US) — batch, low cost. */
    ACH,
    /** SWIFT/cross-border wire. */
    WIRE
}
