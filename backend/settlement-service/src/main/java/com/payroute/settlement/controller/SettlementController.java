package com.payroute.settlement.controller;

import com.payroute.settlement.dto.request.SettlementBatchRequest;
import com.payroute.settlement.dto.response.ApiResponse;
import com.payroute.settlement.dto.response.PagedResponse;
import com.payroute.settlement.dto.response.SettlementBatchResponse;
import com.payroute.settlement.entity.BatchStatus;
import com.payroute.settlement.entity.RailType;
import com.payroute.settlement.service.SettlementService;
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
@RequestMapping("/api/v1/settlement")
@RequiredArgsConstructor
@Tag(name = "Settlement Management", description = "Settlement batch creation and management endpoints")
public class SettlementController {

    private final SettlementService settlementService;

    @Operation(summary = "Create a settlement batch", description = "Create a new settlement batch for a rail and period")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Batch created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/batches")
    public ResponseEntity<ApiResponse<SettlementBatchResponse>> createBatch(
            @Valid @RequestBody SettlementBatchRequest request) {
        SettlementBatchResponse response = settlementService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Settlement batch created successfully"));
    }

    @Operation(summary = "List settlement batches", description = "Retrieve paginated list of settlement batches")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batches retrieved")
    })
    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<PagedResponse<SettlementBatchResponse>>> getBatches(
            @Parameter(description = "Filter by rail") @RequestParam(required = false) RailType rail,
            @Parameter(description = "Filter by status") @RequestParam(required = false) BatchStatus status,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        PagedResponse<SettlementBatchResponse> response = settlementService.getBatches(rail, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get batch by ID", description = "Retrieve a single settlement batch")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Batch not found")
    })
    @GetMapping("/batches/{id}")
    public ResponseEntity<ApiResponse<SettlementBatchResponse>> getBatchById(@PathVariable Long id) {
        SettlementBatchResponse response = settlementService.getBatchById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
