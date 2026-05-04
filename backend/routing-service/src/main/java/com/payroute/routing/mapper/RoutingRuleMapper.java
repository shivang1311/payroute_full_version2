package com.payroute.routing.mapper;

import com.payroute.routing.dto.request.RoutingRuleRequest;
import com.payroute.routing.dto.response.PagedResponse;
import com.payroute.routing.dto.response.RoutingRuleResponse;
import com.payroute.routing.entity.RoutingRule;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoutingRuleMapper {

    RoutingRuleResponse toResponse(RoutingRule entity);

    List<RoutingRuleResponse> toResponseList(List<RoutingRule> entities);

    RoutingRule toEntity(RoutingRuleRequest request);

    void updateEntity(RoutingRuleRequest request, @MappingTarget RoutingRule entity);

    default PagedResponse<RoutingRuleResponse> toPagedResponse(Page<RoutingRule> page) {
        return PagedResponse.<RoutingRuleResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
