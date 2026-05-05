package com.payroute.iam.service;

import com.payroute.iam.client.PartyServiceClient;
import com.payroute.iam.dto.response.PagedResponse;
import com.payroute.iam.dto.response.UserResponse;
import com.payroute.iam.entity.Role;
import com.payroute.iam.entity.User;
import com.payroute.iam.exception.DuplicateResourceException;
import com.payroute.iam.exception.ResourceNotFoundException;
import com.payroute.iam.mapper.UserMapper;
import com.payroute.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private static final Set<Role> ALLOWED_STAFF_ROLES =
            Set.of(Role.OPERATIONS, Role.COMPLIANCE, Role.RECONCILIATION);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PartyServiceClient partyServiceClient;

    /**
     * Admin-only: provision a new staff user (OPERATIONS / COMPLIANCE / RECONCILIATION).
     * CUSTOMER and ADMIN roles cannot be created here — customers self-register, and ADMIN
     * is provisioned via DB seed only.
     */
    @Transactional
    public UserResponse createStaffUser(String username, String email, String phone,
                                        String password, Role role) {
        if (role == null || !ALLOWED_STAFF_ROLES.contains(role)) {
            throw new IllegalArgumentException(
                    "Role must be one of OPERATIONS, COMPLIANCE, RECONCILIATION");
        }
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with email '" + email + "' already exists.");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Phone number '" + phone + "' is already registered.");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .mustChangePassword(true) // force rotation on first login
                .build();

        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    public PagedResponse<UserResponse> getAllUsers(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        return userMapper.toPagedResponse(page);
    }

    /** Active users for a given role — used by notification broadcast fan-out. */
    public List<UserResponse> getUsersByRole(Role role) {
        return userRepository.findByRoleAndActiveTrue(role).stream()
                .map(userMapper::toResponse)
                .toList();
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return userMapper.toResponse(user);
    }

    /**
     * Set or change the caller's transaction PIN.
     *
     * <p>First-time setup (existing PIN is null): only the new PIN is required.
     * Subsequent changes: the caller's account password must be supplied and verified
     * before the new PIN is accepted — the same security gate the user requested.
     */
    @Transactional
    public void setOwnTransactionPin(Long callerId, String newPin, String passwordIfChanging) {
        if (newPin == null || !newPin.matches("^\\d{4,6}$")) {
            throw new IllegalArgumentException("Transaction PIN must be 4 to 6 digits");
        }

        User user = userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + callerId));

        boolean alreadyHasPin = user.getTransactionPinHash() != null && !user.getTransactionPinHash().isBlank();
        if (alreadyHasPin) {
            // Changing an existing PIN: require the account password as a second factor
            if (passwordIfChanging == null || passwordIfChanging.isBlank()) {
                throw new IllegalArgumentException(
                        "Current password is required to change the transaction PIN");
            }
            if (!passwordEncoder.matches(passwordIfChanging, user.getPasswordHash())) {
                throw new com.payroute.iam.exception.InvalidCredentialsException(
                        "Current password is incorrect");
            }
        }

        user.setTransactionPinHash(passwordEncoder.encode(newPin));
        userRepository.save(user);
        log.info("Transaction PIN {} for user id={}", alreadyHasPin ? "changed" : "set", user.getId());
    }

    /**
     * Verify a transaction PIN. Returns true if the PIN matches; false otherwise.
     * Throws if the user has no PIN set (forces customer through the setup flow first).
     */
    public boolean verifyTransactionPin(Long userId, String pin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        if (user.getTransactionPinHash() == null || user.getTransactionPinHash().isBlank()) {
            throw new IllegalStateException("Transaction PIN has not been set");
        }
        if (pin == null) return false;
        return passwordEncoder.matches(pin, user.getTransactionPinHash());
    }

    /**
     * Self-update: the authenticated caller updates their own email and/or phone.
     * Username and role are intentionally not editable here (admins can change role
     * via the existing endpoint; usernames are immutable to keep audit trails sane).
     */
    @Transactional
    public UserResponse updateOwnProfile(Long callerId, String email, String phone) {
        User user = userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + callerId));

        boolean phoneChanged = false;

        if (email != null && !email.isBlank() && !email.equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new DuplicateResourceException("Email '" + email + "' is already in use.");
            }
            user.setEmail(email);
        }
        if (phone != null && !phone.isBlank() && !phone.equals(user.getPhone())) {
            if (userRepository.existsByPhone(phone)) {
                throw new DuplicateResourceException("Phone '" + phone + "' is already in use.");
            }
            user.setPhone(phone);
            phoneChanged = true;
        }

        user = userRepository.save(user);

        // For customers, the same phone number lives on the linked party row too —
        // keep it in sync so beneficiary lookups and account directory stay accurate.
        if (phoneChanged && user.getPartyId() != null && user.getRole() == Role.CUSTOMER) {
            syncPhoneToParty(user.getPartyId(), user.getPhone());
        }

        return userMapper.toResponse(user);
    }

    @SuppressWarnings("unchecked")
    private void syncPhoneToParty(Long partyId, String phone) {
        try {
            Map<String, Object> resp = partyServiceClient.getParty(partyId);
            Map<String, Object> party = resp == null ? null : (Map<String, Object>) resp.get("data");
            if (party == null) {
                log.warn("Party {} not found while syncing phone — skipping", partyId);
                return;
            }
            // Build a full PartyRequest preserving existing values; only override phone
            Map<String, Object> req = new HashMap<>();
            req.put("name", party.get("name"));
            req.put("type", party.get("type"));
            req.put("country", party.get("country"));
            req.put("riskRating", party.get("riskRating"));
            req.put("status", party.get("status"));
            req.put("phone", phone);
            partyServiceClient.updateParty(partyId, req);
            log.info("Synced phone to party {} for customer", partyId);
        } catch (Exception e) {
            // Log but don't fail the whole profile update — user-table change has already committed
            log.warn("Failed to sync phone to party {}: {}", partyId, e.getMessage());
        }
    }

    @Transactional
    public UserResponse updateUser(Long id, String username, String email, String phone) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (username != null) {
            user.setUsername(username);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (phone != null) {
            user.setPhone(phone);
        }

        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUserRole(Long id, Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setRole(role);
        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setActive(false);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}
