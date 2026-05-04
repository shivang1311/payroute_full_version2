package com.payroute.payment.mapper;

import com.payroute.payment.dto.response.ValidationResultResponse;
import com.payroute.payment.entity.ValidationResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ValidationResultMapper {

    ValidationResultResponse toResponse(ValidationResult entity);

    List<ValidationResultResponse> toResponseList(List<ValidationResult> entities);
}
