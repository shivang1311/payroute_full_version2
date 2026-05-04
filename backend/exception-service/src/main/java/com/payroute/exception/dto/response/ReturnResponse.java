package com.payroute.exception.dto.response;

import com.payroute.exception.entity.ReturnStatus;
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
public class ReturnResponse {

    private Long id;
    private Long paymentId;
    private String reasonCode;
    private String reasonDesc;
    private BigDecimal amount;
    private String currency;
    private ReturnStatus status;
    private Long version;
    private LocalDate returnDate;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
