package com.payroute.party.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Mirrors ledger-service {@code LedgerPostRequest}. Used to seed an opening
 * balance entry when a new INR customer account is created.
 *
 * <p>{@code paymentId} is a sentinel ({@code 0}) for opening-balance entries —
 * real payments start at id 1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerPostRequest {
    private Long paymentId;
    private Long accountId;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private String narrative;
}
