package com.payroute.payment.mapper;

import com.payroute.payment.dto.request.PaymentInitiationRequest;
import com.payroute.payment.dto.response.PaymentResponse;
import com.payroute.payment.entity.PaymentOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = ValidationResultMapper.class)
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "initiatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    PaymentOrder toEntity(PaymentInitiationRequest request);

    @Mapping(target = "validationResults", ignore = true)
    PaymentResponse toResponse(PaymentOrder entity);

    List<PaymentResponse> toResponseList(List<PaymentOrder> entities);
}
