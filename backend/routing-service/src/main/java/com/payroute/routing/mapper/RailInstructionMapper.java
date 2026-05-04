package com.payroute.routing.mapper;

import com.payroute.routing.dto.response.PagedResponse;
import com.payroute.routing.dto.response.RailInstructionResponse;
import com.payroute.routing.entity.RailInstruction;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RailInstructionMapper {

    RailInstructionResponse toResponse(RailInstruction entity);

    List<RailInstructionResponse> toResponseList(List<RailInstruction> entities);

    default PagedResponse<RailInstructionResponse> toPagedResponse(Page<RailInstruction> page) {
        return PagedResponse.<RailInstructionResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
