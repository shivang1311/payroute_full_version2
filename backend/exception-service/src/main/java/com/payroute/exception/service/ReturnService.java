package com.payroute.exception.service;

import com.payroute.exception.client.LedgerServiceClient;
import com.payroute.exception.client.NotificationServiceClient;
import com.payroute.exception.dto.client.BroadcastNotificationRequest;
import com.payroute.exception.dto.client.LedgerPostRequest;
import com.payroute.exception.dto.client.NotificationRequest;
import com.payroute.exception.dto.request.ReturnRequest;
import com.payroute.exception.dto.response.PagedResponse;
import com.payroute.exception.dto.response.ReturnResponse;
import com.payroute.exception.entity.ReturnItem;
import com.payroute.exception.entity.ReturnStatus;
import com.payroute.exception.exception.ResourceNotFoundException;
import com.payroute.exception.mapper.ReturnItemMapper;
import com.payroute.exception.repository.ReturnItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReturnService {

    private final ReturnItemRepository returnItemRepository;
    private final ReturnItemMapper returnItemMapper;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    @Transactional
    public ReturnResponse createReturn(ReturnRequest request) {
        ReturnItem returnItem = returnItemMapper.toEntity(request);
        returnItem.setStatus(ReturnStatus.NOTIFIED);
        returnItem.setReturnDate(LocalDate.now());
        returnItem = returnItemRepository.save(returnItem);
        log.info("Created return item {} for payment {}", returnItem.getId(), request.getPaymentId());

        // Fan out to every RECONCILIATION analyst — they need to post the reversal
        // before the cycle closes.
        try {
            notificationServiceClient.broadcast(BroadcastNotificationRequest.builder()
                    .role("RECONCILIATION")
                    .title("New Return Received")
                    .message("Return for payment #" + request.getPaymentId()
                            + " (" + request.getReasonCode() + ") needs to be processed.")
                    .category("EXCEPTION")
                    .severity("WARNING")
                    .referenceType("RETURN")
                    .referenceId(returnItem.getId())
                    .build());
        } catch (Exception e) {
            log.error("Failed to broadcast new-return notification: {}", e.getMessage());
        }

        return returnItemMapper.toResponse(returnItem);
    }

    public PagedResponse<ReturnResponse> getReturns(ReturnStatus status, Pageable pageable) {
        Page<ReturnItem> page;
        if (status != null) {
            page = returnItemRepository.findByStatus(status, pageable);
        } else {
            page = returnItemRepository.findAll(pageable);
        }
        return returnItemMapper.toPagedResponse(page);
    }

    public ReturnResponse getReturnById(Long id) {
        ReturnItem returnItem = returnItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnItem", "id", id));
        return returnItemMapper.toResponse(returnItem);
    }

    @Transactional
    public ReturnResponse processReturn(Long id) {
        ReturnItem returnItem = returnItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnItem", "id", id));

        if (returnItem.getStatus() != ReturnStatus.NOTIFIED) {
            throw new IllegalStateException("Return item must be in NOTIFIED status to process");
        }

        returnItem.setStatus(ReturnStatus.PROCESSING);
        returnItemRepository.save(returnItem);

        // Post mirror REVERSAL entries for every original ledger entry of the payment.
        // Ledger-service's /entries/{paymentId}/reverse creates offsetting entries for debit+credit+fee atomically.
        try {
            ledgerServiceClient.reversePayment(returnItem.getPaymentId());

            returnItem.setStatus(ReturnStatus.POSTED);
            returnItem.setProcessedAt(LocalDateTime.now());
            log.info("Return item {} processed; mirror REVERSAL entries posted for payment {}",
                    id, returnItem.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to post reversal entries for return {}: {}", id, e.getMessage());
            returnItem.setStatus(ReturnStatus.NOTIFIED); // rollback status
        }

        returnItem = returnItemRepository.save(returnItem);

        // Send notification
        try {
            notificationServiceClient.sendNotification(NotificationRequest.builder()
                    .userId(0L)
                    .title("Return Processed")
                    .message("Return " + id + " for payment " + returnItem.getPaymentId() + " has been processed")
                    .category("EXCEPTION")
                    .severity("INFO")
                    .referenceType("RETURN")
                    .referenceId(id)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send return notification: {}", e.getMessage());
        }

        return returnItemMapper.toResponse(returnItem);
    }
}
