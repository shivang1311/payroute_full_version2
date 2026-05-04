package com.payroute.ledger.dto.response;

import com.payroute.ledger.entity.FeeType;
import com.payroute.ledger.entity.RailType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeScheduleResponse {

    private Long id;
    private String product;
    private RailType rail;
    private FeeType feeType;
    private BigDecimal value;
    private BigDecimal minFee;
    private BigDecimal maxFee;
    private String currency;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean active;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
