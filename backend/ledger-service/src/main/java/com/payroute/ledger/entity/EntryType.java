package com.payroute.ledger.entity;

/**
 * Ledger row classification. {@link #DEBIT}/{@link #FEE}/{@link #TAX} reduce
 * the running balance; {@link #CREDIT}/{@link #REVERSAL} increase it.
 */
public enum EntryType {
    /** Outflow from the account (e.g. payment debit). */
    DEBIT,
    /** Inflow to the account (e.g. payment credit, opening balance). */
    CREDIT,
    /** Service charge booked to the bank's revenue account. */
    FEE,
    /** Tax component (rare; treated as a debit). */
    TAX,
    /** Reversal of a prior posting; reverses the original sign. */
    REVERSAL
}
