package com.payroute.exception.mapper;

import com.payroute.exception.dto.request.ReturnRequest;
import com.payroute.exception.dto.response.PagedResponse;
import com.payroute.exception.dto.response.ReturnResponse;
import com.payroute.exception.entity.ReturnItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReturnItemMapper {

    ReturnResponse toResponse(ReturnItem entity);

    List<ReturnResponse> toResponseList(List<ReturnItem> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "returnDate", ignore = true)
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ReturnItem toEntity(ReturnRequest request);

    default PagedResponse<ReturnResponse> toPagedResponse(Page<ReturnItem> page) {
        return PagedResponse.<ReturnResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
