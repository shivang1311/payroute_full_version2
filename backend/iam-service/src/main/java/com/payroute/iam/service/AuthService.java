package com.payroute.iam.service;

import com.payroute.iam.client.PartyServiceClient;
import com.payroute.iam.dto.request.LoginRequest;
import com.payroute.iam.dto.request.RefreshTokenRequest;
import com.payroute.iam.dto.request.RegisterRequest;
import com.payroute.iam.dto.response.AuthResponse;
import com.payroute.iam.entity.RefreshToken;
import com.payroute.iam.entity.Role;
import com.payroute.iam.entity.User;
import com.payroute.iam.exception.DuplicateResourceException;
import com.payroute.iam.exception.InvalidCredentialsException;
import com.payroute.iam.mapper.UserMapper;
import com.payroute.iam.repository.RefreshTokenRepository;
import com.payroute.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final PartyServiceClient partyServiceClient;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already taken. Please choose a different username.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("An account with email '" + request.getEmail() + "' already exists.");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Phone number '" + request.getPhone() + "' is already registered.");
        }

        // Public self-registration is ALWAYS a CUSTOMER. Any role supplied in the
        // request body is intentionally ignored — staff users (OPERATIONS, COMPLIANCE,
        // RECONCILIATION, ADMIN) can only be created via the admin-only endpoint
        // POST /api/v1/users/staff in UserController.
        if (request.getRole() != null && request.getRole() != Role.CUSTOMER) {
            log.warn("Ignoring non-CUSTOMER role '{}' in self-registration for username '{}'",
                    request.getRole(), request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .active(true)
                .build();

        user = userRepository.save(user);

        // Auto-create party for CUSTOMER role
        if (user.getRole() == Role.CUSTOMER) {
            try {
                Long partyId = createPartyForCustomer(user, request.getName());
                user.setPartyId(partyId);
                user = userRepository.save(user);
                log.info("Auto-created party {} for customer user {}", partyId, user.getId());
            } catch (Exception e) {
                log.error("Failed to auto-create party for user {}: {}", user.getId(), e.getMessage());
                // Registration still succeeds; admin can link party later
            }
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user.getId(), refreshToken);

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user.getId(), refreshToken);

        auditService.logAction(user.getId(), "LOGIN", "USER", user.getId(), null, null);

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken existingToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (existingToken.isRevoked()) {
            throw new InvalidCredentialsException("Refresh token has been revoked");
        }

        if (existingToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("Refresh token has expired");
        }

        existingToken.setRevoked(true);
        refreshTokenRepository.save(existingToken);

        User user = userRepository.findById(existingToken.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("User not found for refresh token"));

        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user.getId(), newRefreshToken);

        return buildAuthResponse(accessToken, newRefreshToken, user);
    }

    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    /**
     * Verifies the caller's current password and rotates it to the new one.
     * Also clears the {@code mustChangePassword} flag and revokes all existing
     * refresh tokens (forcing other sessions to re-login).
     */
    public AuthResponse changePassword(Long userId, String currentPassword, String newPassword) {
        if (currentPassword == null || newPassword == null) {
            throw new IllegalArgumentException("Current and new passwords are required");
        }
        if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{8,}$")) {
            throw new IllegalArgumentException(
                    "New password must be at least 8 characters and include uppercase, lowercase, digit, and special character");
        }
        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must differ from current password");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user = userRepository.save(user);

        // Invalidate all refresh tokens — caller will get a fresh pair below
        refreshTokenRepository.deleteByUserId(user.getId());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken();
        saveRefreshToken(user.getId(), refreshToken);

        auditService.logAction(user.getId(), "PASSWORD_CHANGE", "USER", user.getId(), null, null);
        log.info("Password changed for user id={}", user.getId());

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    private void saveRefreshToken(Long userId, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .token(token)
                .expiresAt(LocalDateTime.now().plusSeconds(
                        jwtService.getRefreshTokenExpirationMs() / 1000))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    @SuppressWarnings("unchecked")
    private Long createPartyForCustomer(User user, String fullName) {
        Map<String, Object> partyRequest = new HashMap<>();
        partyRequest.put("name", fullName);
        partyRequest.put("type", "INDIVIDUAL");
        partyRequest.put("phone", user.getPhone());
        partyRequest.put("country", "IND");
        partyRequest.put("riskRating", "LOW");

        Map<String, Object> response = partyServiceClient.createParty(partyRequest);
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return ((Number) data.get("id")).longValue();
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(
                        jwtService.getAccessTokenExpirationMs() / 1000))
                .user(userMapper.toResponse(user))
                .build();
    }
}
