package com.payroute.party.controller;

import com.payroute.party.dto.request.AccountRequest;
import com.payroute.party.dto.response.AccountResponse;
import com.payroute.party.dto.response.AccountValidationResponse;
import com.payroute.party.dto.response.ApiResponse;
import com.payroute.party.dto.response.PagedResponse;
import com.payroute.party.service.AccountDirectoryService;
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
 * REST endpoints for the {@link com.payroute.party.entity.AccountDirectory} —
 * account CRUD, validation by ID/account-number/IFSC, and the helper used by
 * other services to verify ownership before reading sensitive data.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Account Directory", description = "Account directory CRUD and validation endpoints")
public class AccountController {

    private final AccountDirectoryService accountDirectoryService;

    @Operation(summary = "List accounts", description = "Retrieve a paginated list of accounts with optional party filter")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Accounts retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AccountResponse>>> getAccounts(
            @Parameter(description = "Filter by party ID") @RequestParam(required = false) Long partyId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PagedResponse<AccountResponse> response = accountDirectoryService.getAccounts(partyId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Create a new account", description = "Create a new account directory entry")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Account already exists")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody AccountRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Username", required = false) String actingUser) {
        AccountResponse response = accountDirectoryService.createAccount(request, actingUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Account created successfully"));
    }

    @Operation(summary = "Get account by ID", description = "Retrieve a single account by its ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(@PathVariable Long id) {
        AccountResponse response = accountDirectoryService.getAccountById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Update account", description = "Update an existing account directory entry")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Username", required = false) String actingUser) {
        AccountResponse response = accountDirectoryService.updateAccount(id, request, actingUser);
        return ResponseEntity.ok(ApiResponse.success(response, "Account updated successfully"));
    }

    @Operation(summary = "Delete account", description = "Soft delete an account directory entry")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Account deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Username", required = false) String actingUser) {
        accountDirectoryService.deleteAccount(id, actingUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Validate account", description = "Validate an account by account number and IFSC/IBAN. Used by payment-service via Feign.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Validation result returned")
    })
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<AccountValidationResponse>> validateAccount(
            @Parameter(description = "Account number", required = true) @RequestParam String accountNumber,
            @Parameter(description = "IFSC or IBAN code", required = true) @RequestParam String ifscIban) {
        AccountValidationResponse response = accountDirectoryService.validateAccount(accountNumber, ifscIban);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Validate account by ID", description = "Validate an account by its numeric ID. Used by payment-service via Feign.")
    @GetMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<AccountValidationResponse>> validateAccountById(@PathVariable Long id) {
        AccountValidationResponse response = accountDirectoryService.validateById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Lookup account by alias", description = "Find an account by its alias")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found for the given alias")
    })
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<AccountResponse>> lookupByAlias(
            @Parameter(description = "Account alias", required = true) @RequestParam String alias) {
        AccountResponse response = accountDirectoryService.findByAlias(alias);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Resolve account by typed alias",
            description = "Resolve an account by VPA/UPI, PHONE, EMAIL, or NAME alias. Implements §2.5 Account Directory alias routing.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account resolved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid alias type"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No account found for alias")
    })
    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<AccountResponse>> resolveByAlias(
            @Parameter(description = "Alias type: VPA | PHONE | EMAIL | NAME", required = true)
            @RequestParam String aliasType,
            @Parameter(description = "Alias value", required = true)
            @RequestParam String value) {
        AccountResponse response = accountDirectoryService.resolveByAlias(aliasType, value);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
