package com.payroute.party.entity;

/**
 * Lifecycle state of a {@link Party}. Only ACTIVE parties may transact.
 */
public enum PartyStatus {
    /** Normal operating state — payments and account activity allowed. */
    ACTIVE,
    /** Dormant; no recent activity. Read-only. */
    INACTIVE,
    /** Blocked by ops/compliance — all activity rejected until reactivated. */
    SUSPENDED
}
