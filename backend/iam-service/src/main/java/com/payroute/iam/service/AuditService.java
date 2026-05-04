package com.payroute.iam.service;

import com.payroute.iam.dto.response.AuditLogResponse;
import com.payroute.iam.dto.response.PagedResponse;
import com.payroute.iam.entity.AuditLog;
import com.payroute.iam.mapper.AuditLogMapper;
import com.payroute.iam.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Async
    public void logAction(Long userId, String action, String entityType, Long entityId,
                          String details, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId != null ? String.valueOf(entityId) : null)
                .details(details)
                .ipAddress(ipAddress)
                .createdAt(LocalDateTime.now())
                .build();
        auditLogRepository.save(auditLog);
    }

    public PagedResponse<AuditLogResponse> getAuditLogs(Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findAll(pageable);
        return toPagedResponse(page);
    }

    public PagedResponse<AuditLogResponse> getAuditLogsByUser(Long userId, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findByUserId(userId, pageable);
        return toPagedResponse(page);
    }

    private PagedResponse<AuditLogResponse> toPagedResponse(Page<AuditLog> page) {
        List<AuditLogResponse> content = page.getContent().stream()
                .map(auditLogMapper::toResponse)
                .toList();

        return PagedResponse.<AuditLogResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
