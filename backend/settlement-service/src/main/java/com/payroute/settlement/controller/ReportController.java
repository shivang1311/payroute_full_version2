package com.payroute.settlement.controller;

import com.payroute.settlement.dto.request.ReportRequest;
import com.payroute.settlement.dto.response.ApiResponse;
import com.payroute.settlement.dto.response.PagedResponse;
import com.payroute.settlement.dto.response.PaymentReportResponse;
import com.payroute.settlement.service.ReportService;
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
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Report Management", description = "Payment report generation and retrieval endpoints")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Generate a report", description = "Generate a new payment report")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Report generation started"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<PaymentReportResponse>> generateReport(
            @Valid @RequestBody ReportRequest request) {
        PaymentReportResponse response = reportService.generateReport(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Report generated successfully"));
    }

    @Operation(summary = "List reports", description = "Retrieve paginated list of payment reports")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reports retrieved")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PaymentReportResponse>>> getReports(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        PagedResponse<PaymentReportResponse> response = reportService.getReports(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get report by ID", description = "Retrieve a single payment report")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Report found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Report not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentReportResponse>> getReportById(@PathVariable Long id) {
        PaymentReportResponse response = reportService.getReportById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
