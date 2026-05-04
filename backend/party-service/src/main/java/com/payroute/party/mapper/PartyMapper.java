package com.payroute.party.mapper;

import com.payroute.party.dto.request.PartyRequest;
import com.payroute.party.dto.response.PagedResponse;
import com.payroute.party.dto.response.PartyResponse;
import com.payroute.party.entity.Party;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PartyMapper {

    PartyResponse toResponse(Party party);

    List<PartyResponse> toResponseList(List<Party> parties);

    Party toEntity(PartyRequest request);

    void updateEntity(PartyRequest request, @MappingTarget Party party);

    default PagedResponse<PartyResponse> toPagedResponse(Page<Party> page) {
        return PagedResponse.<PartyResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
