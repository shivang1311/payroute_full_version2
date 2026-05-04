package com.payroute.compliance.entity;

public enum HoldStatus {
    /** Hold has been placed and is awaiting compliance review. */
    ACTIVE,
    /** Analyst released the hold; payment resumed downstream processing. */
    RELEASED,
    /** Analyst escalated the hold for senior review. */
    ESCALATED,
    /** Analyst rejected the hold; payment was failed permanently. */
    REJECTED
}
