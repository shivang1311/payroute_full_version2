package com.payroute.ledger.dto.request;

import com.payroute.ledger.entity.EntryType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerPostRequest {

    @NotNull(message = "Payment ID is required")
    private Long paymentId;

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @NotNull(message = "Entry type is required")
    private EntryType entryType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 16, fraction = 2, message = "Amount format invalid")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;

    @Size(max = 500, message = "Narrative must not exceed 500 characters")
    private String narrative;
}
