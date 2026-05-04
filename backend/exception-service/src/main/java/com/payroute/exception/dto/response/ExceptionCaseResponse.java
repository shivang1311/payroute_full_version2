package com.payroute.exception.dto.response;

import com.payroute.exception.entity.ExceptionCategory;
import com.payroute.exception.entity.ExceptionPriority;
import com.payroute.exception.entity.ExceptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCaseResponse {

    private Long id;
    private Long paymentId;
    private ExceptionCategory category;
    private String description;
    private Long ownerId;
    private ExceptionStatus status;
    private String resolution;
    private ExceptionPriority priority;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private String createdBy;
    private String updatedBy;
}
