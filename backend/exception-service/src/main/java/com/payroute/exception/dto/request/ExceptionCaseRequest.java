package com.payroute.exception.dto.request;

import com.payroute.exception.entity.ExceptionCategory;
import com.payroute.exception.entity.ExceptionPriority;
import com.payroute.exception.entity.ExceptionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionCaseRequest {

    // NOTE: paymentId / category / priority are required on CREATE only.
    // The same DTO is reused for partial PUT updates (e.g. Ops setting just status + resolution),
    // so we rely on the service layer for create-time validation and the mapper's
    // null-property-ignore strategy for updates.
    private Long paymentId;

    private ExceptionCategory category;

    private String description;

    private Long ownerId;

    private ExceptionStatus status;

    private String resolution;

    private ExceptionPriority priority;
}
