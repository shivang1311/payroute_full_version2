package com.payroute.exception.mapper;

import com.payroute.exception.dto.request.ExceptionCaseRequest;
import com.payroute.exception.dto.response.ExceptionCaseResponse;
import com.payroute.exception.dto.response.PagedResponse;
import com.payroute.exception.entity.ExceptionCase;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ExceptionCaseMapper {

    ExceptionCaseResponse toResponse(ExceptionCase entity);

    List<ExceptionCaseResponse> toResponseList(List<ExceptionCase> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "resolvedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ExceptionCase toEntity(ExceptionCaseRequest request);

    /**
     * Partial update — fields whose value is null on the request DTO are left untouched on the
     * existing entity. This makes the controller's PUT behave like a PATCH so callers can send
     * just {status, resolution} (typical Ops "Update Status" flow) without nuking description,
     * category, priority, ownerId, etc.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntity(ExceptionCaseRequest request, @MappingTarget ExceptionCase entity);

    default PagedResponse<ExceptionCaseResponse> toPagedResponse(Page<ExceptionCase> page) {
        return PagedResponse.<ExceptionCaseResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
