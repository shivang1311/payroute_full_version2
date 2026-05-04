package com.payroute.ledger.mapper;

import com.payroute.ledger.dto.request.LedgerPostRequest;
import com.payroute.ledger.dto.response.LedgerEntryResponse;
import com.payroute.ledger.entity.LedgerEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LedgerEntryMapper {

    LedgerEntryResponse toResponse(LedgerEntry entity);

    List<LedgerEntryResponse> toResponseList(List<LedgerEntry> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "balanceAfter", ignore = true)
    @Mapping(target = "entryDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    LedgerEntry toEntity(LedgerPostRequest request);
}
