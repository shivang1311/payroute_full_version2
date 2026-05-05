package com.payroute.ledger.entity;

/**
 * Fee computation mode for a {@link FeeSchedule}.
 */
public enum FeeType {
    /** Fixed amount regardless of payment value (e.g. ₹25 per NEFT). */
    FLAT,
    /** A percentage of the payment amount (subject to min/max clamps). */
    PERCENTAGE
}
