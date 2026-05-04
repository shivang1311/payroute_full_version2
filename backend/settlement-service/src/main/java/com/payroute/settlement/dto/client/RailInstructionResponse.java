package com.payroute.settlement.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RailInstructionResponse {

    private Long id;
    private Long paymentId;
    private String rail;
    private String status;
    private BigDecimal amount;
    private String currency;
    private BigDecimal fee;
    private LocalDateTime createdAt;
}
