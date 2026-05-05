package com.payroute.notification.controller;

import com.payroute.notification.dto.request.BroadcastRequest;
import com.payroute.notification.dto.request.NotificationRequest;
import com.payroute.notification.dto.response.ApiResponse;
import com.payroute.notification.dto.response.NotificationCountResponse;
import com.payroute.notification.dto.response.NotificationResponse;
import com.payroute.notification.dto.response.PagedResponse;
import com.payroute.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification Management", description = "Notification endpoints for sending and managing notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Send a notification", description = "Create and send a new notification")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Notification sent successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> send(@Valid @RequestBody NotificationRequest request) {
        NotificationResponse response = notificationService.send(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Notification sent successfully"));
    }

    @Operation(
            summary = "Broadcast a notification to all users with a given role",
            description = "Fan-out: creates one notification per active user with the requested role. "
                    + "Used by other services for events that should alert every analyst of a kind "
                    + "(e.g. a new compliance hold → all COMPLIANCE users).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Notifications broadcast"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> broadcast(@Valid @RequestBody BroadcastRequest request) {
        List<NotificationResponse> created = notificationService.broadcast(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created,
                        "Broadcast sent to " + created.size() + " " + request.getRole() + " user(s)"));
    }

    @Operation(summary = "Get user notifications", description = "Retrieve paginated notifications for the authenticated user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notifications retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getByUser(
            @Parameter(description = "User ID from header") @RequestHeader(value = "X-Username", required = false) String userId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<NotificationResponse> response = notificationService.getByUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get unread notification count", description = "Get count of unread notifications for the authenticated user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Unread count retrieved")
    })
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<NotificationCountResponse>> getUnreadCount(
            @RequestHeader(value = "X-Username", required = false) String userId) {
        NotificationCountResponse response = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Mark notification as read", description = "Mark a single notification as read")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Notification not found")
    })
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable Long id) {
        NotificationResponse response = notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Notification marked as read"));
    }

    @Operation(summary = "Mark all notifications as read", description = "Mark all notifications as read for the authenticated user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All notifications marked as read")
    })
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@RequestHeader(value = "X-Username", required = false) String userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "All notifications marked as read"));
    }
}
