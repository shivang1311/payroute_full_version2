package com.payroute.exception.mapper;

import com.payroute.exception.dto.response.PagedResponse;
import com.payroute.exception.dto.response.ReconciliationResponse;
import com.payroute.exception.entity.ReconciliationRecord;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReconciliationRecordMapper {

    ReconciliationResponse toResponse(ReconciliationRecord entity);

    List<ReconciliationResponse> toResponseList(List<ReconciliationRecord> entities);

    default PagedResponse<ReconciliationResponse> toPagedResponse(Page<ReconciliationRecord> page) {
        return PagedResponse.<ReconciliationResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
