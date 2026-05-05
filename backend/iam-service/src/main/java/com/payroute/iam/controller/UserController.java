package com.payroute.iam.controller;

import com.payroute.iam.dto.response.ApiResponse;
import com.payroute.iam.dto.response.PagedResponse;
import com.payroute.iam.dto.response.UserResponse;
import com.payroute.iam.entity.Role;
import com.payroute.iam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User CRUD and management endpoints")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "Create staff user (admin only)",
            description = "Provision a new OPERATIONS, COMPLIANCE or RECONCILIATION user. "
                    + "Caller must have ADMIN role (X-User-Role header set by API gateway).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Staff user created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid role"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Username or email already exists")
    })
    @PostMapping("/staff")
    public ResponseEntity<ApiResponse<UserResponse>> createStaffUser(
            @RequestHeader(value = "X-User-Role", required = false) String callerRole,
            @RequestBody Map<String, String> request) {

        if (!"ADMIN".equalsIgnoreCase(callerRole)) {
            throw new AccessDeniedException("Only ADMIN can create staff users");
        }

        String roleStr = request.get("role");
        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Role must be one of OPERATIONS, COMPLIANCE, RECONCILIATION"));
        }

        UserResponse response = userService.createStaffUser(
                request.get("username"),
                request.get("email"),
                request.get("phone"),
                request.get("password"),
                role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Staff user created successfully"));
    }

    @Operation(
            summary = "Get my profile",
            description = "Authenticated user fetches their own profile.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile returned"),
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(userId)));
    }

    @Operation(
            summary = "Set or change my transaction PIN",
            description = "First-time setup: only the new PIN is required. "
                    + "Subsequent changes require the account password as a second factor.")
    @PostMapping("/me/pin")
    public ResponseEntity<ApiResponse<Void>> setMyTransactionPin(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, String> body) {
        userService.setOwnTransactionPin(userId, body.get("pin"), body.get("currentPassword"));
        return ResponseEntity.ok(ApiResponse.success(null, "Transaction PIN updated"));
    }

    @Operation(
            summary = "Verify my transaction PIN",
            description = "Caller verifies their own PIN. Returns {\"valid\":true|false}.")
    @PostMapping("/me/pin/verify")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verifyMyTransactionPin(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, String> body) {
        boolean ok = userService.verifyTransactionPin(userId, body.get("pin"));
        return ResponseEntity.ok(ApiResponse.success(Map.of("valid", ok)));
    }

    @Operation(
            summary = "Verify another user's transaction PIN (service-to-service)",
            description = "Used by payment-service to gate every customer payment.")
    @PostMapping("/{id}/pin/verify")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verifyUserTransactionPin(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        boolean ok = userService.verifyTransactionPin(id, body.get("pin"));
        return ResponseEntity.ok(ApiResponse.success(Map.of("valid", ok)));
    }

    @Operation(
            summary = "Update my profile",
            description = "Authenticated user updates their own email and/or phone. "
                    + "Username and role cannot be changed here.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or phone already in use")
    })
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, String> body) {
        UserResponse updated = userService.updateOwnProfile(
                userId, body.get("email"), body.get("phone"));
        return ResponseEntity.ok(ApiResponse.success(updated, "Profile updated successfully"));
    }

    @Operation(summary = "List all users", description = "Retrieve a paginated list of all users")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getAllUsers(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "id") String sort,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        PagedResponse<UserResponse> response = userService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "List active users by role (service-to-service)",
            description = "Used by notification-service to fan out broadcasts to e.g. all COMPLIANCE analysts.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid role")
    })
    @GetMapping("/by-role")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsersByRole(
            @Parameter(description = "Role name (e.g. COMPLIANCE, OPERATIONS)") @RequestParam("role") Role role) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUsersByRole(role)));
    }

    @Operation(summary = "Get user by ID", description = "Retrieve a single user by their ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Update user details", description = "Update username, email, and phone for a user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        UserResponse response = userService.updateUser(
                id,
                request.get("username"),
                request.get("email"),
                request.get("phone"));
        return ResponseEntity.ok(ApiResponse.success(response, "User updated successfully"));
    }

    @Operation(summary = "Update user role", description = "Change the role assigned to a user")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User role updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        Role role = Role.valueOf(request.get("role"));
        UserResponse response = userService.updateUserRole(id, role);
        return ResponseEntity.ok(ApiResponse.success(response, "User role updated successfully"));
    }

    @Operation(summary = "Deactivate user", description = "Soft delete a user by setting active to false")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "User deactivated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
