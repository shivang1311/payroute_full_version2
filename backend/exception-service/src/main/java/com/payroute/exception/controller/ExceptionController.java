package com.payroute.exception.controller;

import com.payroute.exception.dto.request.ExceptionCaseRequest;
import com.payroute.exception.dto.response.ApiResponse;
import com.payroute.exception.dto.response.ExceptionCaseResponse;
import com.payroute.exception.dto.response.PagedResponse;
import com.payroute.exception.entity.ExceptionStatus;
import com.payroute.exception.service.ExceptionService;
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
@RequestMapping("/api/v1/exceptions")
@RequiredArgsConstructor
@Tag(name = "Exception Management", description = "Exception case CRUD and management endpoints")
public class ExceptionController {

    private final ExceptionService exceptionService;

    @Operation(summary = "Create an exception case", description = "Create a new exception case for a payment")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Exception case created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ExceptionCaseResponse>> createException(
            @Valid @RequestBody ExceptionCaseRequest request) {
        ExceptionCaseResponse response = exceptionService.createException(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Exception case created successfully"));
    }

    @Operation(summary = "List exception cases", description = "Retrieve paginated list of exception cases with optional filters")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exception cases retrieved")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ExceptionCaseResponse>>> getExceptions(
            @Parameter(description = "Filter by status") @RequestParam(required = false) ExceptionStatus status,
            @Parameter(description = "Filter by owner ID — used by 'My Queue'") @RequestParam(required = false) Long ownerId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        PagedResponse<ExceptionCaseResponse> response = exceptionService.getExceptions(status, ownerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get exception case by ID", description = "Retrieve a single exception case")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exception case found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Exception case not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExceptionCaseResponse>> getExceptionById(@PathVariable Long id) {
        ExceptionCaseResponse response = exceptionService.getExceptionById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Assign an exception case",
            description = "Assign / reassign / unassign an exception case. Setting an owner on an OPEN case " +
                    "moves it to IN_PROGRESS automatically.")
    @PutMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<ExceptionCaseResponse>> assignException(
            @PathVariable Long id,
            @RequestParam(value = "ownerId", required = false) Long ownerId) {
        ExceptionCaseResponse response = exceptionService.assignOwner(id, ownerId);
        return ResponseEntity.ok(ApiResponse.success(response,
                ownerId == null ? "Exception unassigned" : "Exception assigned"));
    }

    @Operation(summary = "Auto-close exception cases for a payment",
            description = "Mark all OPEN / IN_PROGRESS exception cases for the given payment as RESOLVED. " +
                    "Called by payment-service when a payment is retried or otherwise unblocked.")
    @PutMapping("/payment/{paymentId}/auto-close")
    public ResponseEntity<ApiResponse<Integer>> autoCloseForPayment(
            @PathVariable Long paymentId,
            @RequestParam(required = false) String reason) {
        int closed = exceptionService.autoCloseForPayment(paymentId, reason);
        return ResponseEntity.ok(ApiResponse.success(closed,
                "Auto-closed " + closed + " exception case(s) for payment " + paymentId));
    }

    @Operation(summary = "Update exception case", description = "Update an existing exception case")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exception case updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Exception case not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExceptionCaseResponse>> updateException(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionCaseRequest request) {
        ExceptionCaseResponse response = exceptionService.updateException(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Exception case updated successfully"));
    }
}
