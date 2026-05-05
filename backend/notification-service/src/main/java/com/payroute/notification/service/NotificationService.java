package com.payroute.notification.service;

import com.payroute.notification.client.IamServiceClient;
import com.payroute.notification.dto.client.IamUser;
import com.payroute.notification.dto.request.BroadcastRequest;
import com.payroute.notification.dto.request.NotificationRequest;
import com.payroute.notification.dto.response.NotificationCountResponse;
import com.payroute.notification.dto.response.NotificationResponse;
import com.payroute.notification.dto.response.PagedResponse;
import com.payroute.notification.entity.Notification;
import com.payroute.notification.exception.ResourceNotFoundException;
import com.payroute.notification.mapper.NotificationMapper;
import com.payroute.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final IamServiceClient iamServiceClient;

    @Transactional
    public NotificationResponse send(NotificationRequest request) {
        Notification notification = notificationMapper.toEntity(request);
        notification.setIsRead(false);
        notification = notificationRepository.save(notification);
        log.info("Notification sent to user {} with title: {}", request.getUserId(), request.getTitle());
        return notificationMapper.toResponse(notification);
    }

    /**
     * Fan-out: create one notification per active user with the requested role.
     * Used for events that should reach every analyst of a kind (e.g. a new
     * compliance hold landing in the queue).
     */
    @Transactional
    public List<NotificationResponse> broadcast(BroadcastRequest request) {
        List<IamUser> users;
        try {
            users = iamServiceClient.usersByRole(request.getRole());
        } catch (Exception e) {
            log.error("Failed to look up users for role {} during broadcast: {}",
                    request.getRole(), e.getMessage());
            return List.of();
        }

        if (users.isEmpty()) {
            log.warn("No active users found for role {} — broadcast skipped", request.getRole());
            return List.of();
        }

        List<Notification> toSave = new ArrayList<>(users.size());
        for (IamUser u : users) {
            if (u.getUsername() == null || u.getUsername().isBlank()) continue;
            toSave.add(Notification.builder()
                    .userId(u.getUsername())
                    .title(request.getTitle())
                    .message(request.getMessage())
                    .category(request.getCategory())
                    .severity(request.getSeverity())
                    .referenceType(request.getReferenceType())
                    .referenceId(request.getReferenceId())
                    .isRead(false)
                    .build());
        }

        List<Notification> saved = notificationRepository.saveAll(toSave);
        log.info("Broadcast '{}' to {} {} user(s)",
                request.getTitle(), saved.size(), request.getRole());
        return saved.stream().map(notificationMapper::toResponse).toList();
    }

    public PagedResponse<NotificationResponse> getByUser(String userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return notificationMapper.toPagedResponse(page);
    }

    public PagedResponse<NotificationResponse> getUnreadByUser(String userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
        return notificationMapper.toPagedResponse(page);
    }

    @Transactional
    public NotificationResponse markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        notification = notificationRepository.save(notification);
        log.info("Notification {} marked as read", id);
        return notificationMapper.toResponse(notification);
    }

    @Transactional
    public void markAllAsRead(String userId) {
        Page<Notification> unreadPage = notificationRepository
                .findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, Pageable.unpaged());
        unreadPage.getContent().forEach(notification -> {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
        });
        notificationRepository.saveAll(unreadPage.getContent());
        log.info("All notifications marked as read for user {}", userId);
    }

    public NotificationCountResponse getUnreadCount(String userId) {
        long count = notificationRepository.countByUserIdAndIsRead(userId, false);
        return NotificationCountResponse.builder()
                .userId(userId)
                .unreadCount(count)
                .build();
    }
}
