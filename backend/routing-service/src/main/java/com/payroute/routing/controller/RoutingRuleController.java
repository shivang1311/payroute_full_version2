package com.payroute.routing.controller;

import com.payroute.routing.dto.request.RoutingRuleRequest;
import com.payroute.routing.dto.response.ApiResponse;
import com.payroute.routing.dto.response.PagedResponse;
import com.payroute.routing.dto.response.RoutingRuleResponse;
import com.payroute.routing.service.RoutingRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routing/rules")
@RequiredArgsConstructor
@Tag(name = "Routing Rules", description = "CRUD operations for routing rules")
public class RoutingRuleController {

    private final RoutingRuleService routingRuleService;

    @GetMapping
    @Operation(summary = "List all routing rules", description = "Paginated list of active routing rules")
    public ResponseEntity<ApiResponse<PagedResponse<RoutingRuleResponse>>> getAllRules(
            @PageableDefault(size = 20, sort = "priority", direction = Sort.Direction.ASC) Pageable pageable) {
        PagedResponse<RoutingRuleResponse> response = routingRuleService.getAllRules(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @Operation(summary = "Create a routing rule")
    public ResponseEntity<ApiResponse<RoutingRuleResponse>> createRule(
            @Valid @RequestBody RoutingRuleRequest request) {
        RoutingRuleResponse response = routingRuleService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Routing rule created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a routing rule by ID")
    public ResponseEntity<ApiResponse<RoutingRuleResponse>> getRuleById(@PathVariable Long id) {
        RoutingRuleResponse response = routingRuleService.getRuleById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a routing rule")
    public ResponseEntity<ApiResponse<RoutingRuleResponse>> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody RoutingRuleRequest request) {
        RoutingRuleResponse response = routingRuleService.updateRule(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Routing rule updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a routing rule")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        routingRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
