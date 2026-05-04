package com.payroute.party.mapper;

import com.payroute.party.dto.request.AccountRequest;
import com.payroute.party.dto.response.AccountResponse;
import com.payroute.party.dto.response.PagedResponse;
import com.payroute.party.entity.AccountDirectory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(source = "party.id", target = "partyId")
    @Mapping(source = "party.name", target = "partyName")
    AccountResponse toResponse(AccountDirectory account);

    List<AccountResponse> toResponseList(List<AccountDirectory> accounts);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "party", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    AccountDirectory toEntity(AccountRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "party", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntity(AccountRequest request, @MappingTarget AccountDirectory account);

    default PagedResponse<AccountResponse> toPagedResponse(Page<AccountDirectory> page) {
        return PagedResponse.<AccountResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
