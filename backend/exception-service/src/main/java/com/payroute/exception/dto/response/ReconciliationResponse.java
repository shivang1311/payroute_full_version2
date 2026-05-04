package com.payroute.exception.dto.response;

import com.payroute.exception.entity.ReconResult;
import com.payroute.exception.entity.ReconSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResponse {

    private Long id;
    private ReconSource source;
    private Long referenceId;
    private Long counterpartId;
    private LocalDate reconDate;
    private ReconResult result;
    private BigDecimal amount;
    private String currency;
    private String notes;
    private Boolean resolved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
