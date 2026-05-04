package com.payroute.ledger.controller;

import com.payroute.ledger.dto.request.FeeScheduleRequest;
import com.payroute.ledger.dto.response.ApiResponse;
import com.payroute.ledger.dto.response.FeeScheduleResponse;
import com.payroute.ledger.service.FeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger/fees")
@RequiredArgsConstructor
@Tag(name = "Fee Schedule", description = "Fee schedule management APIs")
public class FeeScheduleController {

    private final FeeService feeService;

    @PostMapping
    @Operation(summary = "Create a new fee schedule")
    public ResponseEntity<ApiResponse<FeeScheduleResponse>> createFeeSchedule(
            @Valid @RequestBody FeeScheduleRequest request) {
        FeeScheduleResponse response = feeService.createFeeSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fee schedule created", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a fee schedule by ID")
    public ResponseEntity<ApiResponse<FeeScheduleResponse>> getFeeScheduleById(@PathVariable Long id) {
        FeeScheduleResponse response = feeService.getFeeScheduleById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Get all fee schedules")
    public ResponseEntity<ApiResponse<List<FeeScheduleResponse>>> getAllFeeSchedules() {
        List<FeeScheduleResponse> response = feeService.getAllFeeSchedules();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active fee schedules")
    public ResponseEntity<ApiResponse<List<FeeScheduleResponse>>> getActiveFeeSchedules() {
        List<FeeScheduleResponse> response = feeService.getActiveFeeSchedules();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a fee schedule")
    public ResponseEntity<ApiResponse<FeeScheduleResponse>> updateFeeSchedule(
            @PathVariable Long id,
            @Valid @RequestBody FeeScheduleRequest request) {
        FeeScheduleResponse response = feeService.updateFeeSchedule(id, request);
        return ResponseEntity.ok(ApiResponse.success("Fee schedule updated", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a fee schedule (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deactivateFeeSchedule(@PathVariable Long id) {
        feeService.deactivateFeeSchedule(id);
        return ResponseEntity.ok(ApiResponse.success("Fee schedule deactivated", null));
    }
}
