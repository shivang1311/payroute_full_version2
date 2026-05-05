package com.payroute.exception.service;

import com.payroute.exception.client.NotificationServiceClient;
import com.payroute.exception.dto.client.NotificationRequest;
import com.payroute.exception.dto.request.ExceptionCaseRequest;
import com.payroute.exception.dto.response.ExceptionCaseResponse;
import com.payroute.exception.dto.response.ExceptionStatsResponse;
import com.payroute.exception.dto.response.PagedResponse;
import com.payroute.exception.entity.ExceptionCase;
import com.payroute.exception.entity.ExceptionCategory;
import com.payroute.exception.entity.ExceptionStatus;
import com.payroute.exception.exception.ResourceNotFoundException;
import com.payroute.exception.mapper.ExceptionCaseMapper;
import com.payroute.exception.repository.ExceptionCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExceptionService {

    private final ExceptionCaseRepository exceptionCaseRepository;
    private final ExceptionCaseMapper exceptionCaseMapper;
    private final NotificationServiceClient notificationServiceClient;

    @Transactional
    public ExceptionCaseResponse createException(ExceptionCaseRequest request) {
        // Required fields on create — validated here since the DTO no longer carries
        // @NotNull (the same DTO is reused for partial PUT updates).
        if (request.getPaymentId() == null) {
            throw new IllegalArgumentException("paymentId is required");
        }
        if (request.getCategory() == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (request.getPriority() == null) {
            throw new IllegalArgumentException("priority is required");
        }

        ExceptionCase exceptionCase = exceptionCaseMapper.toEntity(request);
        if (exceptionCase.getStatus() == null) {
            exceptionCase.setStatus(ExceptionStatus.OPEN);
        }
        exceptionCase = exceptionCaseRepository.save(exceptionCase);
        log.info("Created exception case {} for payment {}", exceptionCase.getId(), request.getPaymentId());

        // Send notification
        try {
            notificationServiceClient.sendNotification(NotificationRequest.builder()
                    .userId(0L)
                    .title("Exception Case Created")
                    .message("Exception case " + exceptionCase.getId() + " created for payment " + request.getPaymentId())
                    .category("EXCEPTION")
                    .severity(request.getPriority().name())
                    .referenceType("EXCEPTION_CASE")
                    .referenceId(exceptionCase.getId())
                    .build());
        } catch (Exception e) {
            log.error("Failed to send exception notification: {}", e.getMessage());
        }

        return exceptionCaseMapper.toResponse(exceptionCase);
    }

    public PagedResponse<ExceptionCaseResponse> getExceptions(ExceptionStatus status, Pageable pageable) {
        return getExceptions(status, null, null, pageable);
    }

    public PagedResponse<ExceptionCaseResponse> getExceptions(ExceptionStatus status, Long ownerId, Pageable pageable) {
        return getExceptions(status, null, ownerId, pageable);
    }

    public PagedResponse<ExceptionCaseResponse> getExceptions(
            ExceptionStatus status, ExceptionCategory category, Long ownerId, Pageable pageable) {
        Page<ExceptionCase> page;
        if (status != null && category != null && ownerId != null) {
            page = exceptionCaseRepository.findByStatusAndCategoryAndOwnerId(status, category, ownerId, pageable);
        } else if (status != null && category != null) {
            page = exceptionCaseRepository.findByStatusAndCategory(status, category, pageable);
        } else if (status != null && ownerId != null) {
            page = exceptionCaseRepository.findByStatusAndOwnerId(status, ownerId, pageable);
        } else if (category != null && ownerId != null) {
            page = exceptionCaseRepository.findByCategoryAndOwnerId(category, ownerId, pageable);
        } else if (status != null) {
            page = exceptionCaseRepository.findByStatus(status, pageable);
        } else if (category != null) {
            page = exceptionCaseRepository.findByCategory(category, pageable);
        } else if (ownerId != null) {
            page = exceptionCaseRepository.findByOwnerId(ownerId, pageable);
        } else {
            page = exceptionCaseRepository.findAll(pageable);
        }
        return exceptionCaseMapper.toPagedResponse(page);
    }

    public ExceptionCaseResponse getExceptionById(Long id) {
        ExceptionCase exceptionCase = exceptionCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExceptionCase", "id", id));
        return exceptionCaseMapper.toResponse(exceptionCase);
    }

    /**
     * Mark all OPEN / IN_PROGRESS exception cases for the given payment as RESOLVED.
     * Called by payment-service when a payment is retried or otherwise unblocked,
     * so the queue cleans itself up automatically.
     *
     * @return the number of cases that were closed
     */
    @Transactional
    public int autoCloseForPayment(Long paymentId, String resolutionNote) {
        List<ExceptionCase> cases = exceptionCaseRepository.findByPaymentId(paymentId);
        int closed = 0;
        for (ExceptionCase c : cases) {
            if (c.getStatus() == ExceptionStatus.OPEN || c.getStatus() == ExceptionStatus.IN_PROGRESS) {
                c.setStatus(ExceptionStatus.RESOLVED);
                c.setResolution(resolutionNote != null ? resolutionNote : "Auto-resolved by system retry");
                c.setResolvedAt(LocalDateTime.now());
                exceptionCaseRepository.save(c);
                closed++;
            }
        }
        if (closed > 0) {
            log.info("Auto-closed {} exception case(s) for payment id={}", closed, paymentId);
        }
        return closed;
    }

    /**
     * Assign / reassign / unassign an exception case. Pass {@code null} ownerId to unassign.
     * Status transitions to IN_PROGRESS when assigning a previously-OPEN case so the
     * queue reflects "someone is working on it".
     */
    @Transactional
    public ExceptionCaseResponse assignOwner(Long id, Long ownerId) {
        ExceptionCase c = exceptionCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExceptionCase", "id", id));
        c.setOwnerId(ownerId);
        if (ownerId != null && c.getStatus() == ExceptionStatus.OPEN) {
            c.setStatus(ExceptionStatus.IN_PROGRESS);
        }
        c = exceptionCaseRepository.save(c);
        log.info("Exception case {} assigned to ownerId={}", id, ownerId);
        return exceptionCaseMapper.toResponse(c);
    }

    @Transactional
    public ExceptionCaseResponse updateException(Long id, ExceptionCaseRequest request) {
        ExceptionCase exceptionCase = exceptionCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExceptionCase", "id", id));

        exceptionCaseMapper.updateEntity(request, exceptionCase);

        if (request.getStatus() == ExceptionStatus.RESOLVED && exceptionCase.getResolvedAt() == null) {
            exceptionCase.setResolvedAt(LocalDateTime.now());
        }

        exceptionCase = exceptionCaseRepository.save(exceptionCase);
        log.info("Updated exception case {}", id);
        return exceptionCaseMapper.toResponse(exceptionCase);
    }

    /**
     * Global aggregate counts for the Exception Queue dashboard cards.
     * Always operates on the full dataset (no filter applied) so the
     * header stats always reflect the true state of the queue.
     */
    public ExceptionStatsResponse getStats() {
        // Status counts
        List<Object[]> rows = exceptionCaseRepository.countByStatus();
        Map<ExceptionStatus, Long> byStatus = new EnumMap<>(ExceptionStatus.class);
        long total = 0;
        for (Object[] row : rows) {
            ExceptionStatus status = (ExceptionStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status, count);
            total += count;
        }

        // SLA breach: non-terminal cases older than 24 hours
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        long slaBreached = exceptionCaseRepository.countSlaBreached(
                List.of(ExceptionStatus.RESOLVED, ExceptionStatus.CLOSED), threshold);

        return ExceptionStatsResponse.builder()
                .total(total)
                .open(byStatus.getOrDefault(ExceptionStatus.OPEN, 0L))
                .inProgress(byStatus.getOrDefault(ExceptionStatus.IN_PROGRESS, 0L))
                .escalated(byStatus.getOrDefault(ExceptionStatus.ESCALATED, 0L))
                .resolved(byStatus.getOrDefault(ExceptionStatus.RESOLVED, 0L))
                .closed(byStatus.getOrDefault(ExceptionStatus.CLOSED, 0L))
                .slaBreached(slaBreached)
                .build();
    }
}
