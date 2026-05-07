package com.payroute.iam.service;

import com.payroute.iam.dto.response.AuditLogResponse;
import com.payroute.iam.dto.response.PagedResponse;
import com.payroute.iam.entity.AuditLog;
import com.payroute.iam.entity.User;
import com.payroute.iam.mapper.AuditLogMapper;
import com.payroute.iam.repository.AuditLogRepository;
import com.payroute.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final UserRepository userRepository;

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

    /**
     * Map a page of AuditLog entities to response DTOs, enriching each row with
     * the username via a single batched lookup. We do the join in code (not SQL)
     * because audit logs are append-only and live forever — pulling usernames
     * on every request keeps "username at the time of the action" decoupled
     * from later renames.
     */
    private PagedResponse<AuditLogResponse> toPagedResponse(Page<AuditLog> page) {
        Set<Long> userIds = page.getContent().stream()
                .map(AuditLog::getUserId)
                .collect(Collectors.toSet());

        Map<Long, String> usernameById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        List<AuditLogResponse> content = page.getContent().stream()
                .map(log -> {
                    AuditLogResponse r = auditLogMapper.toResponse(log);
                    r.setUsername(usernameById.get(log.getUserId()));
                    return r;
                })
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
