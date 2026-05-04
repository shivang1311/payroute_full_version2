package com.payroute.exception.controller;

import com.payroute.exception.dto.response.ApiResponse;
import com.payroute.exception.dto.response.PagedResponse;
import com.payroute.exception.dto.response.ReconciliationResponse;
import com.payroute.exception.entity.ReconResult;
import com.payroute.exception.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Reconciliation", description = "Reconciliation record management and execution endpoints")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @Operation(summary = "Get reconciliation records", description = "Retrieve paginated reconciliation records with optional filters")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Records retrieved")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ReconciliationResponse>>> getRecords(
            @Parameter(description = "Filter by reconciliation date") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reconDate,
            @Parameter(description = "Filter by result") @RequestParam(required = false) ReconResult result,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        PagedResponse<ReconciliationResponse> response =
                reconciliationService.getReconciliationRecords(reconDate, result, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Run reconciliation", description = "Execute reconciliation for a given date")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reconciliation completed")
    })
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<List<ReconciliationResponse>>> runReconciliation(
            @Parameter(description = "Date to reconcile") @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<ReconciliationResponse> response = reconciliationService.runReconciliation(date);
        return ResponseEntity.ok(ApiResponse.success(response, "Reconciliation completed for " + date));
    }
}
