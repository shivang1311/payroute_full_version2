package com.payroute.settlement.mapper;

import com.payroute.settlement.dto.request.SettlementBatchRequest;
import com.payroute.settlement.dto.response.PagedResponse;
import com.payroute.settlement.dto.response.SettlementBatchResponse;
import com.payroute.settlement.entity.SettlementBatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SettlementBatchMapper {

    SettlementBatchResponse toResponse(SettlementBatch entity);

    List<SettlementBatchResponse> toResponseList(List<SettlementBatch> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "totalCount", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "netAmount", ignore = true)
    @Mapping(target = "totalFees", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "postedDate", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    SettlementBatch toEntity(SettlementBatchRequest request);

    default PagedResponse<SettlementBatchResponse> toPagedResponse(Page<SettlementBatch> page) {
        return PagedResponse.<SettlementBatchResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
