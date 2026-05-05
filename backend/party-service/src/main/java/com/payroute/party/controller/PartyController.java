package com.payroute.party.controller;

import com.payroute.party.dto.request.PartyRequest;
import com.payroute.party.dto.response.ApiResponse;
import com.payroute.party.dto.response.PagedResponse;
import com.payroute.party.dto.response.PartyResponse;
import com.payroute.party.entity.PartyStatus;
import com.payroute.party.entity.PartyType;
import com.payroute.party.service.PartyService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for {@link com.payroute.party.entity.Party} CRUD —
 * individuals and corporates. Used by registration, KYC, and admin party
 * management screens.
 */
@RestController
@RequestMapping("/api/v1/parties")
@RequiredArgsConstructor
@Tag(name = "Party Management", description = "Party CRUD and management endpoints")
public class PartyController {

    private final PartyService partyService;

    @Operation(summary = "List all parties", description = "Retrieve a paginated list of parties with optional filters")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Parties retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PartyResponse>>> getAllParties(
            @Parameter(description = "Filter by status") @RequestParam(required = false) PartyStatus status,
            @Parameter(description = "Filter by type") @RequestParam(required = false) PartyType type,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "id") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        PagedResponse<PartyResponse> response = partyService.getAllParties(status, type, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Create a new party", description = "Create a new party record")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Party created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PartyResponse>> createParty(@Valid @RequestBody PartyRequest request) {
        PartyResponse response = partyService.createParty(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Party created successfully"));
    }

    @Operation(summary = "Get party by ID", description = "Retrieve a single party by its ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Party found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Party not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PartyResponse>> getPartyById(@PathVariable Long id) {
        PartyResponse response = partyService.getPartyById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Update party", description = "Update an existing party")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Party updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Party not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PartyResponse>> updateParty(
            @PathVariable Long id,
            @Valid @RequestBody PartyRequest request) {
        PartyResponse response = partyService.updateParty(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Party updated successfully"));
    }

    @Operation(summary = "Delete party", description = "Soft delete a party")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Party deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Party not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParty(@PathVariable Long id) {
        partyService.deleteParty(id);
        return ResponseEntity.noContent().build();
    }
}
