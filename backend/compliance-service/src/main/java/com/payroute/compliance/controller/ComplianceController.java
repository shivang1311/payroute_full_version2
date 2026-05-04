package com.payroute.compliance.controller;

import com.payroute.compliance.dto.request.ComplianceScreenRequest;
import com.payroute.compliance.dto.request.HoldReleaseRequest;
import com.payroute.compliance.dto.response.*;
import com.payroute.compliance.entity.CheckResult;
import com.payroute.compliance.entity.HoldStatus;
import com.payroute.compliance.service.ComplianceService;
import com.payroute.compliance.service.HoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance Management", description = "Compliance screening, checks, and hold management endpoints")
public class ComplianceController {

    private final ComplianceService complianceService;
    private final HoldService holdService;

    @Operation(summary = "Screen a payment", description = "Run compliance screening checks on a payment")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Screening completed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/screen")
    public ResponseEntity<ApiResponse<ComplianceScreenResponse>> screenPayment(
            @Valid @RequestBody ComplianceScreenRequest request) {
        ComplianceScreenResponse response = complianceService.screenPayment(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Compliance screening completed"));
    }

    @Operation(summary = "Get compliance checks", description = "Retrieve compliance checks with optional filters")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Checks retrieved successfully")
    })
    @GetMapping("/checks")
    public ResponseEntity<ApiResponse<PagedResponse<ComplianceCheckResponse>>> getChecks(
            @Parameter(description = "Filter by payment ID") @RequestParam(required = false) Long paymentId,
            @Parameter(description = "Filter by result") @RequestParam(required = false) CheckResult result,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        PagedResponse<ComplianceCheckResponse> response = complianceService.getChecks(paymentId, result, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get holds", description = "Retrieve holds with optional status filter")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Holds retrieved successfully")
    })
    @GetMapping("/holds")
    public ResponseEntity<ApiResponse<PagedResponse<HoldResponse>>> getHolds(
            @Parameter(description = "Filter by status") @RequestParam(required = false) HoldStatus status,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<HoldResponse> response = holdService.getHolds(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Release a hold", description = "Release an active compliance hold")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hold released successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Hold not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Hold not in ACTIVE status")
    })
    @PutMapping("/holds/{id}/release")
    public ResponseEntity<ApiResponse<HoldResponse>> releaseHold(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody HoldReleaseRequest request) {
        HoldResponse response = holdService.releaseHold(id, userId, request.getReleaseNotes());
        return ResponseEntity.ok(ApiResponse.success(response, "Hold released successfully"));
    }

    @Operation(
            summary = "Reject a hold",
            description = "Compliance analyst rejects the held payment. Hold is marked REJECTED " +
                    "and the underlying payment is moved to FAILED with the analyst's reason.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hold rejected and payment failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Hold not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Hold not in ACTIVE status")
    })
    @PutMapping("/holds/{id}/reject")
    public ResponseEntity<ApiResponse<HoldResponse>> rejectHold(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody HoldReleaseRequest request) {
        HoldResponse response = holdService.rejectHold(id, userId, request.getReleaseNotes());
        return ResponseEntity.ok(ApiResponse.success(response, "Hold rejected and payment failed"));
    }
}
