package com.payroute.routing.controller;

import com.payroute.routing.dto.request.SlaConfigRequest;
import com.payroute.routing.dto.response.ApiResponse;
import com.payroute.routing.dto.response.SlaConfigResponse;
import com.payroute.routing.service.SlaConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routing/sla-config")
@RequiredArgsConstructor
@Tag(name = "SLA Config", description = "Per-rail SLA configuration for breach detection (§7 Admin, §8 Observability)")
public class SlaConfigController {

    private final SlaConfigService slaConfigService;

    @GetMapping
    @Operation(summary = "List SLA configs")
    public ResponseEntity<ApiResponse<List<SlaConfigResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(slaConfigService.list()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get SLA config by ID")
    public ResponseEntity<ApiResponse<SlaConfigResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(slaConfigService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Create SLA config")
    public ResponseEntity<ApiResponse<SlaConfigResponse>> create(@Valid @RequestBody SlaConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(slaConfigService.create(request), "SLA config created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update SLA config")
    public ResponseEntity<ApiResponse<SlaConfigResponse>> update(@PathVariable Long id,
                                                                 @Valid @RequestBody SlaConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(slaConfigService.update(id, request), "SLA config updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete SLA config")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        slaConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
