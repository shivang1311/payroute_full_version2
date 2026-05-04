package com.payroute.notification.controller;

import com.payroute.notification.dto.request.WebhookEndpointRequest;
import com.payroute.notification.dto.request.WebhookEventRequest;
import com.payroute.notification.dto.response.ApiResponse;
import com.payroute.notification.dto.response.PagedResponse;
import com.payroute.notification.dto.response.WebhookDeliveryResponse;
import com.payroute.notification.dto.response.WebhookEndpointResponse;
import com.payroute.notification.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Outbound webhook endpoints and delivery log")
public class WebhookController {

    private final WebhookService webhookService;

    @Operation(summary = "List endpoints for the calling user")
    @GetMapping("/endpoints")
    public ResponseEntity<ApiResponse<List<WebhookEndpointResponse>>> listEndpoints(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.listForUser(userId)));
    }

    @Operation(summary = "Create a new endpoint")
    @PostMapping("/endpoints")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody WebhookEndpointRequest req) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.create(userId, req),
                "Endpoint created — copy the secret now, it will be masked afterwards"));
    }

    @Operation(summary = "Update an endpoint")
    @PutMapping("/endpoints/{id}")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody WebhookEndpointRequest req) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.update(id, req)));
    }

    @Operation(summary = "Delete an endpoint")
    @DeleteMapping("/endpoints/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        webhookService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Deleted"));
    }

    @Operation(summary = "Rotate the HMAC secret — returns the new secret once")
    @PostMapping("/endpoints/{id}/rotate-secret")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> rotate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.rotateSecret(id),
                "New secret — copy it now"));
    }

    @Operation(summary = "Fire a test delivery")
    @PostMapping("/endpoints/{id}/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(@PathVariable Long id) {
        int enqueued = webhookService.enqueueTest(id);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("enqueued", enqueued),
                "Test delivery enqueued — check the deliveries log"));
    }

    @Operation(summary = "List delivery log",
            description = "All webhook delivery attempts, most recent first. Optionally filter by endpoint.")
    @GetMapping("/deliveries")
    public ResponseEntity<ApiResponse<PagedResponse<WebhookDeliveryResponse>>> listDeliveries(
            @RequestParam(required = false) Long endpointId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<WebhookDeliveryResponse> p = webhookService.listDeliveries(endpointId, pageable);
        PagedResponse<WebhookDeliveryResponse> pr = PagedResponse.<WebhookDeliveryResponse>builder()
                .content(p.getContent()).page(p.getNumber()).size(p.getSize())
                .totalElements(p.getTotalElements()).totalPages(p.getTotalPages()).last(p.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(pr));
    }

    @Operation(summary = "Re-queue a failed or stuck delivery")
    @PostMapping("/deliveries/{id}/retry")
    public ResponseEntity<ApiResponse<WebhookDeliveryResponse>> retry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(webhookService.retry(id), "Re-queued"));
    }

    @Operation(summary = "Internal: publish an event for fan-out",
            description = "Called by other services (payment-service) to enqueue deliveries to matching endpoints")
    @PostMapping("/events")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(@Valid @RequestBody WebhookEventRequest req) {
        int enqueued = webhookService.publish(req);
        return ResponseEntity.ok(ApiResponse.success(Map.of("enqueued", enqueued)));
    }
}
