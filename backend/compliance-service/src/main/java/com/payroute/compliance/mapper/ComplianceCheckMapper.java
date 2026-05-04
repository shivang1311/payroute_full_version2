package com.payroute.compliance.mapper;

import com.payroute.compliance.dto.response.ComplianceCheckResponse;
import com.payroute.compliance.dto.response.PagedResponse;
import com.payroute.compliance.entity.ComplianceCheck;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ComplianceCheckMapper {

    ComplianceCheckResponse toResponse(ComplianceCheck entity);

    List<ComplianceCheckResponse> toResponseList(List<ComplianceCheck> entities);

    default PagedResponse<ComplianceCheckResponse> toPagedResponse(Page<ComplianceCheck> page) {
        return PagedResponse.<ComplianceCheckResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
