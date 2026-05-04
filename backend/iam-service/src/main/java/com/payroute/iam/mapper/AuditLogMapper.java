package com.payroute.iam.mapper;

import com.payroute.iam.dto.response.AuditLogResponse;
import com.payroute.iam.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
