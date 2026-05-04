package com.payroute.compliance.mapper;

import com.payroute.compliance.dto.response.HoldResponse;
import com.payroute.compliance.dto.response.PagedResponse;
import com.payroute.compliance.entity.Hold;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HoldMapper {

    HoldResponse toResponse(Hold entity);

    List<HoldResponse> toResponseList(List<Hold> entities);

    default PagedResponse<HoldResponse> toPagedResponse(Page<Hold> page) {
        return PagedResponse.<HoldResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
