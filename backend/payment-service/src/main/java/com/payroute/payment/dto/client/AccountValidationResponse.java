package com.payroute.payment.dto.client;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountValidationResponse {

    private Long accountId;
    private boolean exists;
    private boolean active;
    private String currency;
    private String partyName;
    private Long partyId;
}
