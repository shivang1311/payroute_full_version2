package com.payroute.exception.controller;

import com.payroute.exception.dto.request.ReturnRequest;
import com.payroute.exception.dto.response.ApiResponse;
import com.payroute.exception.dto.response.PagedResponse;
import com.payroute.exception.dto.response.ReturnResponse;
import com.payroute.exception.entity.ReturnStatus;
import com.payroute.exception.service.ReturnService;
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
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
@Tag(name = "Return Management", description = "Return item CRUD and processing endpoints")
public class ReturnController {

    private final ReturnService returnService;

    @Operation(summary = "Create a return item", description = "Create a new return item for a payment")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Return item created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ReturnResponse>> createReturn(@Valid @RequestBody ReturnRequest request) {
        ReturnResponse response = returnService.createReturn(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Return item created successfully"));
    }

    @Operation(summary = "List return items", description = "Retrieve paginated list of return items")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Return items retrieved")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ReturnResponse>>> getReturns(
            @Parameter(description = "Filter by status") @RequestParam(required = false) ReturnStatus status,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        PagedResponse<ReturnResponse> response = returnService.getReturns(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get return item by ID", description = "Retrieve a single return item")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Return item found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Return item not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReturnResponse>> getReturnById(@PathVariable Long id) {
        ReturnResponse response = returnService.getReturnById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Process a return", description = "Process a return item and post reversal entries to ledger")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Return processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Return item not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Return not in processable status")
    })
    @PostMapping("/{id}/process")
    public ResponseEntity<ApiResponse<ReturnResponse>> processReturn(@PathVariable Long id) {
        ReturnResponse response = returnService.processReturn(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Return processed successfully"));
    }
}
