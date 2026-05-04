package com.payroute.iam.service;

import com.payroute.iam.client.PartyServiceClient;
import com.payroute.iam.dto.response.UserResponse;
import com.payroute.iam.entity.Role;
import com.payroute.iam.entity.User;
import com.payroute.iam.exception.DuplicateResourceException;
import com.payroute.iam.exception.InvalidCredentialsException;
import com.payroute.iam.exception.ResourceNotFoundException;
import com.payroute.iam.mapper.UserMapper;
import com.payroute.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PartyServiceClient partyServiceClient;

    @InjectMocks UserService userService;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(1L).username("alice").email("alice@example.com").phone("9000000001")
                .passwordHash("hashed").role(Role.CUSTOMER).active(true).partyId(101L)
                .build();
        lenient().when(userMapper.toResponse(any(User.class)))
                .thenAnswer(inv -> {
                    User u = inv.getArgument(0);
                    return UserResponse.builder()
                            .id(u.getId()).username(u.getUsername())
                            .email(u.getEmail()).phone(u.getPhone())
                            .role(u.getRole()).active(u.isActive())
                            .partyId(u.getPartyId())
                            .pinSet(u.getTransactionPinHash() != null)
                            .build();
                });
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed-secret");
    }

    // ------------------- createStaffUser -------------------

    @Nested
    @DisplayName("createStaffUser")
    class CreateStaff {

        @Test
        @DisplayName("creates with mustChangePassword=true and the requested role")
        void createsWithFlag() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByPhone(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0); u.setId(99L); return u;
            });

            UserResponse resp = userService.createStaffUser(
                    "ops.bob", "ops.bob@x.com", "9000000099", "Welcome@1", Role.OPERATIONS);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.getRole()).isEqualTo(Role.OPERATIONS);
            assertThat(saved.isMustChangePassword()).isTrue();
            assertThat(saved.isActive()).isTrue();
            assertThat(resp.getUsername()).isEqualTo("ops.bob");
        }

        @Test
        @DisplayName("rejects CUSTOMER role")
        void rejectsCustomerRole() {
            assertThatThrownBy(() -> userService.createStaffUser(
                    "x", "x@x.com", "9000000099", "P@ss12345", Role.CUSTOMER))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects ADMIN role (admins are seeded only)")
        void rejectsAdminRole() {
            assertThatThrownBy(() -> userService.createStaffUser(
                    "x", "x@x.com", "9000000099", "P@ss12345", Role.ADMIN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null role")
        void rejectsNullRole() {
            assertThatThrownBy(() -> userService.createStaffUser(
                    "x", "x@x.com", "9000000099", "P@ss12345", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("propagates DuplicateResourceException on conflict")
        void duplicateUsername() {
            when(userRepository.existsByUsername("ops.bob")).thenReturn(true);
            assertThatThrownBy(() -> userService.createStaffUser(
                    "ops.bob", "ops.bob@x.com", "9000000099", "P@ss12345", Role.OPERATIONS))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    // ------------------- getById / getAll -------------------

    @Nested
    @DisplayName("getUserById")
    class GetById {
        @Test
        void found() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            UserResponse resp = userService.getUserById(1L);
            assertThat(resp.getUsername()).isEqualTo("alice");
        }

        @Test
        void notFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.getUserById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------- updateUser -------------------

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("updates only the non-null fields supplied")
        void partialUpdate() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUser(1L, null, "new@x.com", null);

            assertThat(customer.getUsername()).isEqualTo("alice"); // unchanged
            assertThat(customer.getEmail()).isEqualTo("new@x.com"); // changed
            assertThat(customer.getPhone()).isEqualTo("9000000001"); // unchanged
        }

        @Test
        void notFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.updateUser(99L, "x", null, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------- updateUserRole -------------------

    @Nested
    @DisplayName("updateUserRole")
    class UpdateUserRole {
        @Test
        void changesRole() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateUserRole(1L, Role.OPERATIONS);
            assertThat(customer.getRole()).isEqualTo(Role.OPERATIONS);
        }

        @Test
        void notFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.updateUserRole(99L, Role.ADMIN))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------- deactivateUser -------------------

    @Nested
    @DisplayName("deactivateUser")
    class DeactivateUser {
        @Test
        void softDeletes() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            userService.deactivateUser(1L);
            assertThat(customer.isActive()).isFalse();
            assertThat(customer.getDeletedAt()).isNotNull();
            verify(userRepository).save(customer);
        }

        @Test
        void notFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.deactivateUser(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------- setOwnTransactionPin -------------------

    @Nested
    @DisplayName("setOwnTransactionPin")
    class SetOwnPin {

        @Test
        @DisplayName("first-time setup: only PIN required, no password check")
        void firstTime() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

            userService.setOwnTransactionPin(1L, "1234", null);

            assertThat(customer.getTransactionPinHash()).isEqualTo("hashed-secret");
            verify(userRepository).save(customer);
        }

        @Test
        @DisplayName("change PIN: password is verified before accepting new PIN")
        void changeRequiresPassword() {
            customer.setTransactionPinHash("old-pin-hash");
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("RightP@ss1", "hashed")).thenReturn(true);

            userService.setOwnTransactionPin(1L, "5678", "RightP@ss1");

            assertThat(customer.getTransactionPinHash()).isEqualTo("hashed-secret");
            verify(userRepository).save(customer);
        }

        @Test
        @DisplayName("change PIN: rejects when password is wrong")
        void wrongPassword() {
            customer.setTransactionPinHash("old-pin-hash");
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            assertThatThrownBy(() -> userService.setOwnTransactionPin(1L, "5678", "wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("change PIN: rejects when password is missing")
        void missingPasswordOnChange() {
            customer.setTransactionPinHash("old-pin-hash");
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> userService.setOwnTransactionPin(1L, "5678", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects PIN that's not 4-6 digits")
        void invalidPinFormat() {
            assertThatThrownBy(() -> userService.setOwnTransactionPin(1L, "12", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> userService.setOwnTransactionPin(1L, "1234567", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> userService.setOwnTransactionPin(1L, "abcd", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void userNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.setOwnTransactionPin(99L, "1234", null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------- verifyTransactionPin -------------------

    @Nested
    @DisplayName("verifyTransactionPin")
    class VerifyPin {
        @Test
        void matches() {
            customer.setTransactionPinHash("pin-hash");
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("1234", "pin-hash")).thenReturn(true);

            assertThat(userService.verifyTransactionPin(1L, "1234")).isTrue();
        }

        @Test
        void doesNotMatch() {
            customer.setTransactionPinHash("pin-hash");
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("9999", "pin-hash")).thenReturn(false);

            assertThat(userService.verifyTransactionPin(1L, "9999")).isFalse();
        }

        @Test
        void nullPinReturnsFalse() {
            customer.setTransactionPinHash("pin-hash");
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            assertThat(userService.verifyTransactionPin(1L, null)).isFalse();
        }

        @Test
        @DisplayName("throws when no PIN set yet (forces customer through setup)")
        void noPinSet() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            // customer has no transactionPinHash by default
            assertThatThrownBy(() -> userService.verifyTransactionPin(1L, "1234"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ------------------- updateOwnProfile -------------------

    @Nested
    @DisplayName("updateOwnProfile")
    class UpdateOwnProfile {

        @Test
        @DisplayName("updates email + phone and syncs phone to party-service for customer")
        void happyPath() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(userRepository.existsByEmail("new@x.com")).thenReturn(false);
            when(userRepository.existsByPhone("9999999999")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // party fetch for phone sync
            Map<String, Object> partyResp = new HashMap<>();
            Map<String, Object> data = new HashMap<>();
            data.put("name", "Alice");
            data.put("type", "INDIVIDUAL");
            data.put("country", "IND");
            data.put("riskRating", "LOW");
            data.put("status", "ACTIVE");
            partyResp.put("data", data);
            when(partyServiceClient.getParty(101L)).thenReturn(partyResp);
            when(partyServiceClient.updateParty(anyLong(), any())).thenReturn(new HashMap<>());

            UserResponse resp = userService.updateOwnProfile(1L, "new@x.com", "9999999999");

            assertThat(resp.getEmail()).isEqualTo("new@x.com");
            assertThat(resp.getPhone()).isEqualTo("9999999999");
            verify(partyServiceClient).updateParty(eq(101L), any());
        }

        @Test
        @DisplayName("does not call party-service when phone is unchanged")
        void noPhoneChangeSkipsSync() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(userRepository.existsByEmail("new@x.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateOwnProfile(1L, "new@x.com", null);

            verify(partyServiceClient, never()).updateParty(anyLong(), any());
        }

        @Test
        @DisplayName("rejects duplicate email")
        void duplicateEmail() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(userRepository.existsByEmail("taken@x.com")).thenReturn(true);
            assertThatThrownBy(() -> userService.updateOwnProfile(1L, "taken@x.com", null))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("rejects duplicate phone")
        void duplicatePhone() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(userRepository.existsByPhone("9999999999")).thenReturn(true);
            assertThatThrownBy(() -> userService.updateOwnProfile(1L, null, "9999999999"))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("staff user (no partyId) skips party sync entirely")
        void staffSkipsPartySync() {
            customer.setRole(Role.OPERATIONS);
            customer.setPartyId(null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(userRepository.existsByPhone("9999999999")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateOwnProfile(1L, null, "9999999999");
            verify(partyServiceClient, never()).updateParty(anyLong(), any());
        }

        @Test
        @DisplayName("party-service failure does NOT roll back the local update")
        void partyFailureDoesNotRollback() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
            when(userRepository.existsByPhone("9999999999")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(partyServiceClient.getParty(101L)).thenThrow(new RuntimeException("party-service down"));

            // Should NOT throw — phone already saved locally; party sync is best-effort.
            UserResponse resp = userService.updateOwnProfile(1L, null, "9999999999");
            assertThat(resp.getPhone()).isEqualTo("9999999999");
        }
    }

}
