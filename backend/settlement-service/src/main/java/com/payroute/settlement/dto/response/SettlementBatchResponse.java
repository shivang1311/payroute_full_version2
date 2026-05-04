package com.payroute.settlement.dto.response;

import com.payroute.settlement.entity.BatchStatus;
import com.payroute.settlement.entity.RailType;
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
public class SettlementBatchResponse {

    private Long id;
    private RailType rail;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer totalCount;
    private BigDecimal totalAmount;
    private BigDecimal netAmount;
    private BigDecimal totalFees;
    private String currency;
    private BatchStatus status;
    private LocalDateTime postedDate;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
