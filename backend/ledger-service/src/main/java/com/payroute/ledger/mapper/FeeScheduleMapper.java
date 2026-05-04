package com.payroute.ledger.mapper;

import com.payroute.ledger.dto.request.FeeScheduleRequest;
import com.payroute.ledger.dto.response.FeeScheduleResponse;
import com.payroute.ledger.entity.FeeSchedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FeeScheduleMapper {

    FeeScheduleResponse toResponse(FeeSchedule entity);

    List<FeeScheduleResponse> toResponseList(List<FeeSchedule> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    FeeSchedule toEntity(FeeScheduleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(FeeScheduleRequest request, @MappingTarget FeeSchedule entity);
}
