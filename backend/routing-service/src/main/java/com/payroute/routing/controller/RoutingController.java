package com.payroute.routing.controller;

import com.payroute.routing.dto.request.RouteRequest;
import com.payroute.routing.dto.response.ApiResponse;
import com.payroute.routing.dto.response.PagedResponse;
import com.payroute.routing.dto.response.RailInstructionResponse;
import com.payroute.routing.entity.RailStatus;
import com.payroute.routing.entity.RailType;
import com.payroute.routing.exception.ResourceNotFoundException;
import com.payroute.routing.mapper.RailInstructionMapper;
import com.payroute.routing.repository.RailInstructionRepository;
import com.payroute.routing.service.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/routing")
@RequiredArgsConstructor
@Tag(name = "Routing", description = "Payment routing and rail instruction APIs")
public class RoutingController {

    private final RoutingService routingService;
    private final RailInstructionRepository railInstructionRepository;
    private final RailInstructionMapper railInstructionMapper;

    @PostMapping("/route")
    @Operation(summary = "Route a payment", description = "Evaluates routing rules and assigns a rail to the payment")
    public ResponseEntity<ApiResponse<RailInstructionResponse>> routePayment(
            @Valid @RequestBody RouteRequest request) {
        RailInstructionResponse response = routingService.routePayment(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment routed successfully"));
    }

    @GetMapping("/instructions")
    @Operation(summary = "List rail instructions", description = "Paginated list with optional filters")
    public ResponseEntity<ApiResponse<PagedResponse<RailInstructionResponse>>> getInstructions(
            @RequestParam(required = false) Long paymentId,
            @RequestParam(required = false) RailType rail,
            @RequestParam(required = false) RailStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PagedResponse<RailInstructionResponse> response = railInstructionMapper.toPagedResponse(
                railInstructionRepository.findWithFilters(paymentId, rail, status, pageable));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/stats/by-rail")
    @Operation(summary = "Rail breakdown", description = "Count of rail instructions per rail within a date range")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRailStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : railInstructionRepository.aggregateByRail(from, to)) {
            out.add(Map.of(
                    "rail", row[0] == null ? "UNKNOWN" : row[0].toString(),
                    "count", ((Number) row[1]).longValue()
            ));
        }
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/stats/settlement-eligible")
    @Operation(summary = "Settlement-eligible payment IDs",
            description = "Returns payment IDs of SETTLED rail instructions for a given rail within a completion window — used by settlement-service to build a settlement batch.")
    public ResponseEntity<ApiResponse<List<Long>>> settlementEligible(
            @RequestParam RailType rail,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Long> ids = railInstructionRepository
                .findPaymentIdsByRailAndStatusAndCompletedBetween(rail, RailStatus.SETTLED, from, to);
        return ResponseEntity.ok(ApiResponse.success(ids));
    }

    @GetMapping("/stats/settled-on")
    @Operation(summary = "Settled payment IDs for a single date",
            description = "Distinct payment IDs of SETTLED rail instructions completed on the given date. Used by reconciliation.")
    public ResponseEntity<ApiResponse<List<Long>>> settledOn(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.atTime(java.time.LocalTime.MAX);
        List<Long> ids = railInstructionRepository.findSettledPaymentIdsOnDate(from, to);
        return ResponseEntity.ok(ApiResponse.success(ids));
    }

    @GetMapping("/stats/sla-breaches")
    @Operation(summary = "SLA breach count",
            description = "Count of rail instructions flagged as SLA breaches within the given window (uses breach_notified_at).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> slaBreaches(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Long count = railInstructionRepository.countBreachesBetween(from, to);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count == null ? 0L : count)));
    }

    @GetMapping("/instructions/{id}")
    @Operation(summary = "Get a rail instruction by ID")
    public ResponseEntity<ApiResponse<RailInstructionResponse>> getInstruction(@PathVariable Long id) {
        RailInstructionResponse response = railInstructionRepository.findById(id)
                .map(railInstructionMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("RailInstruction", id));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
