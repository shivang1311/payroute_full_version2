package com.payroute.payment.controller;

import com.payroute.payment.dto.request.ScheduledPaymentRequest;
import com.payroute.payment.dto.response.ApiResponse;
import com.payroute.payment.dto.response.PagedResponse;
import com.payroute.payment.dto.response.ScheduledPaymentResponse;
import com.payroute.payment.service.ScheduledPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/scheduled")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Scheduled Payments", description = "One-time or recurring payment schedules")
public class ScheduledPaymentController {

    private final ScheduledPaymentService service;

    @PostMapping
    @Operation(summary = "Create a scheduled/recurring payment")
    public ResponseEntity<ApiResponse<ScheduledPaymentResponse>> create(
            @Valid @RequestBody ScheduledPaymentRequest req,
            HttpServletRequest http) {
        Long userId = parseLong(http.getHeader("X-User-Id"));
        String createdBy = orDefault(http.getHeader("X-Username"), "SYSTEM");
        String role = http.getHeader("X-User-Role");
        Long partyId = parseLong(http.getHeader("X-Party-Id"));
        return ResponseEntity.ok(ApiResponse.success("Schedule created",
                service.create(req, userId, createdBy, role, partyId)));
    }

    @GetMapping
    @Operation(summary = "List schedules (paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<ScheduledPaymentResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            HttpServletRequest http) {
        // Non-ADMIN roles only see their own schedules
        String role = http.getHeader("X-User-Role");
        Long scope = userId;
        if (!"ADMIN".equalsIgnoreCase(role) && !"OPERATIONS".equalsIgnoreCase(role)) {
            scope = parseLong(http.getHeader("X-User-Id"));
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<ScheduledPaymentResponse> p = service.list(pageable, scope);
        PagedResponse<ScheduledPaymentResponse> pr = PagedResponse.of(
                p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages(), p.isLast());
        return ResponseEntity.ok(ApiResponse.success(pr));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduledPaymentResponse>> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getOne(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a schedule (only ACTIVE/PAUSED)")
    public ResponseEntity<ApiResponse<ScheduledPaymentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ScheduledPaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Schedule updated", service.update(id, req)));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<ScheduledPaymentResponse>> pause(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Paused", service.pause(id)));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<ScheduledPaymentResponse>> resume(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Resumed", service.resume(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a schedule")
    public ResponseEntity<ApiResponse<ScheduledPaymentResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Cancelled", service.cancel(id)));
    }

    private Long parseLong(String s) {
        try { return s == null || s.isBlank() ? null : Long.parseLong(s); }
        catch (NumberFormatException ex) { return null; }
    }

    private String orDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
}
