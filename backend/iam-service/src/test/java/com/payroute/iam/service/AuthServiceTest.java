package com.payroute.iam.service;

import com.payroute.iam.client.PartyServiceClient;
import com.payroute.iam.dto.request.LoginRequest;
import com.payroute.iam.dto.request.RefreshTokenRequest;
import com.payroute.iam.dto.request.RegisterRequest;
import com.payroute.iam.dto.response.AuthResponse;
import com.payroute.iam.dto.response.UserResponse;
import com.payroute.iam.entity.RefreshToken;
import com.payroute.iam.entity.Role;
import com.payroute.iam.entity.User;
import com.payroute.iam.exception.DuplicateResourceException;
import com.payroute.iam.exception.InvalidCredentialsException;
import com.payroute.iam.mapper.UserMapper;
import com.payroute.iam.repository.RefreshTokenRepository;
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

import java.time.LocalDateTime;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserMapper userMapper;
    @Mock AuditService auditService;
    @Mock PartyServiceClient partyServiceClient;

    @InjectMocks AuthService authService;

    private RegisterRequest validRegister;

    @BeforeEach
    void setUp() {
        validRegister = RegisterRequest.builder()
                .name("Alice Sharma")
                .username("alice")
                .email("alice@example.com")
                .password("Strong@1234")
                .phone("9000000001")
                .role(Role.CUSTOMER)
                .build();

        // Token gen + refresh expiration are wired in most paths
        lenient().when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        lenient().when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
        lenient().when(jwtService.getAccessTokenExpirationMs()).thenReturn(900_000L);
        lenient().when(jwtService.getRefreshTokenExpirationMs()).thenReturn(60_000L);
        lenient().when(userMapper.toResponse(any(User.class)))
                .thenReturn(UserResponse.builder().username("alice").role(Role.CUSTOMER).build());
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
    }

    // -------------------- register --------------------

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("creates a new CUSTOMER user and returns tokens")
        void happyPath() {
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
            when(userRepository.existsByPhone("9000000001")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            // Auto-create-party stub
            Map<String, Object> partyResp = new HashMap<>();
            Map<String, Object> data = new HashMap<>();
            data.put("id", 100);
            partyResp.put("data", data);
            when(partyServiceClient.createParty(any())).thenReturn(partyResp);

            AuthResponse resp = authService.register(validRegister);

            assertThat(resp).isNotNull();
            assertThat(resp.getAccessToken()).isEqualTo("access-token");
            assertThat(resp.getRefreshToken()).isEqualTo("refresh-token");

            // The role MUST be coerced to CUSTOMER regardless of what the request claimed.
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(2)).save(captor.capture());
            User saved = captor.getAllValues().get(0);
            assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("ignores ADMIN role smuggled in via the request body (security)")
        void ignoresElevatedRoleInRequest() {
            validRegister.setRole(Role.ADMIN);
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByPhone(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(2L);
                return u;
            });
            Map<String, Object> partyResp = new HashMap<>();
            Map<String, Object> data = new HashMap<>();
            data.put("id", 100);
            partyResp.put("data", data);
            when(partyServiceClient.createParty(any())).thenReturn(partyResp);

            authService.register(validRegister);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getRole()).isEqualTo(Role.CUSTOMER);
        }

        @Test
        @DisplayName("throws when username is taken")
        void duplicateUsername() {
            when(userRepository.existsByUsername("alice")).thenReturn(true);
            assertThatThrownBy(() -> authService.register(validRegister))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("alice");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when email is taken")
        void duplicateEmail() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);
            assertThatThrownBy(() -> authService.register(validRegister))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when phone is taken")
        void duplicatePhone() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByPhone("9000000001")).thenReturn(true);
            assertThatThrownBy(() -> authService.register(validRegister))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("registration succeeds even if party-service is unreachable (best-effort)")
        void survivesPartyServiceFailure() {
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByPhone(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(3L);
                return u;
            });
            when(partyServiceClient.createParty(any())).thenThrow(new RuntimeException("party-service down"));

            // Should NOT throw — the user is still created, party link can be set later.
            AuthResponse resp = authService.register(validRegister);
            assertThat(resp.getAccessToken()).isNotNull();
        }
    }

    // -------------------- login --------------------

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns tokens when credentials are correct")
        void successful() {
            User u = User.builder()
                    .id(1L).username("alice").passwordHash("hashed")
                    .role(Role.CUSTOMER).active(true).build();
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
            when(passwordEncoder.matches("Strong@1234", "hashed")).thenReturn(true);

            AuthResponse resp = authService.login(new LoginRequest("alice", "Strong@1234"));

            assertThat(resp.getAccessToken()).isEqualTo("access-token");
            assertThat(resp.getRefreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
            verify(auditService).logAction(eq(1L), eq("LOGIN"), eq("USER"), eq(1L), any(), any());
        }

        @Test
        @DisplayName("throws when username is unknown")
        void unknownUser() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "x")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("throws when password is wrong")
        void wrongPassword() {
            User u = User.builder().id(1L).username("alice").passwordHash("hashed").role(Role.CUSTOMER).build();
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
            when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "bad")))
                    .isInstanceOf(InvalidCredentialsException.class);
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // -------------------- refreshToken --------------------

    @Nested
    @DisplayName("refreshToken")
    class RefreshTokenFlow {

        @Test
        @DisplayName("rotates the refresh token on success")
        void rotates() {
            RefreshToken existing = RefreshToken.builder()
                    .id(1L).userId(99L).token("old-refresh")
                    .expiresAt(LocalDateTime.now().plusHours(1)).revoked(false).build();
            when(refreshTokenRepository.findByToken("old-refresh")).thenReturn(Optional.of(existing));
            User u = User.builder().id(99L).username("alice").role(Role.CUSTOMER).build();
            when(userRepository.findById(99L)).thenReturn(Optional.of(u));

            AuthResponse resp = authService.refreshToken(new RefreshTokenRequest("old-refresh"));

            assertThat(resp.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(existing.isRevoked()).isTrue(); // old token revoked
            // Mockito sees both the "save the revoked old token" and the "save the new
            // token" calls; assert the total count matches what we expect.
            verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("rejects an unknown refresh token")
        void unknown() {
            when(refreshTokenRepository.findByToken("nope")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("nope")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("rejects a revoked refresh token")
        void revoked() {
            RefreshToken rt = RefreshToken.builder().userId(1L).token("t").revoked(true)
                    .expiresAt(LocalDateTime.now().plusHours(1)).build();
            when(refreshTokenRepository.findByToken("t")).thenReturn(Optional.of(rt));

            assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("t")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("revoked");
        }

        @Test
        @DisplayName("rejects an expired refresh token")
        void expired() {
            RefreshToken rt = RefreshToken.builder().userId(1L).token("t").revoked(false)
                    .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
            when(refreshTokenRepository.findByToken("t")).thenReturn(Optional.of(rt));

            assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("t")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("expired");
        }
    }

    // -------------------- logout --------------------

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("deletes all refresh tokens for the user")
        void deletesAllTokens() {
            authService.logout(42L);
            verify(refreshTokenRepository).deleteByUserId(42L);
        }
    }

    // -------------------- changePassword --------------------

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("happy path: hashes new pwd, clears mustChangePassword flag, revokes sessions, returns fresh tokens")
        void happyPath() {
            User u = User.builder().id(7L).username("alice").passwordHash("old-hash")
                    .role(Role.CUSTOMER).mustChangePassword(true).build();
            when(userRepository.findById(7L)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches("CurrentP@ss1", "old-hash")).thenReturn(true);
            when(passwordEncoder.encode("NewP@ss12345")).thenReturn("new-hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse resp = authService.changePassword(7L, "CurrentP@ss1", "NewP@ss12345");

            assertThat(resp.getAccessToken()).isNotNull();
            assertThat(u.getPasswordHash()).isEqualTo("new-hash");
            assertThat(u.isMustChangePassword()).isFalse();
            verify(refreshTokenRepository).deleteByUserId(7L);
            verify(auditService).logAction(eq(7L), eq("PASSWORD_CHANGE"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("rejects when current password is wrong")
        void wrongCurrentPwd() {
            User u = User.builder().id(7L).passwordHash("old-hash").role(Role.CUSTOMER).build();
            when(userRepository.findById(7L)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword(7L, "wrong", "NewP@ss12345"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("rejects a weak new password (no special char)")
        void weakNewPassword() {
            assertThatThrownBy(() -> authService.changePassword(1L, "Curr3ntP@ss", "weakpass"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects when new password equals current")
        void sameAsOld() {
            assertThatThrownBy(() -> authService.changePassword(1L, "SameP@ss123", "SameP@ss123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("differ");
        }

        @Test
        @DisplayName("rejects when current/new are null/blank")
        void rejectsNulls() {
            assertThatThrownBy(() -> authService.changePassword(1L, null, "NewP@ss12345"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> authService.changePassword(1L, "Curr3ntP@ss", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
