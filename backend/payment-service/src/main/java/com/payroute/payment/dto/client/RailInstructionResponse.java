package com.payroute.payment.dto.client;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RailInstructionResponse {

    private Long id;
    private String rail;
    private String correlationRef;
    private String status;
}
