package com.payroute.settlement.mapper;

import com.payroute.settlement.dto.request.ReportRequest;
import com.payroute.settlement.dto.response.PagedResponse;
import com.payroute.settlement.dto.response.PaymentReportResponse;
import com.payroute.settlement.entity.PaymentReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentReportMapper {

    PaymentReportResponse toResponse(PaymentReport entity);

    List<PaymentReportResponse> toResponseList(List<PaymentReport> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "metrics", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "generatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    PaymentReport toEntity(ReportRequest request);

    default PagedResponse<PaymentReportResponse> toPagedResponse(Page<PaymentReport> page) {
        return PagedResponse.<PaymentReportResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
