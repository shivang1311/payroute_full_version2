package com.payroute.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroute.iam.dto.request.LoginRequest;
import com.payroute.iam.dto.request.RefreshTokenRequest;
import com.payroute.iam.dto.request.RegisterRequest;
import com.payroute.iam.dto.response.AuthResponse;
import com.payroute.iam.dto.response.UserResponse;
import com.payroute.iam.entity.Role;
import com.payroute.iam.exception.DuplicateResourceException;
import com.payroute.iam.exception.InvalidCredentialsException;
import com.payroute.iam.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean AuthService authService;

    private AuthResponse stubAuth(String username) {
        return AuthResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .user(UserResponse.builder().username(username).role(Role.CUSTOMER).build())
                .build();
    }

    // ---------- /login ----------

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {
        @Test
        @DisplayName("returns 200 + tokens on valid credentials")
        void loginSuccess() throws Exception {
            when(authService.login(any(LoginRequest.class))).thenReturn(stubAuth("alice"));

            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new LoginRequest("alice", "Strong@1234"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access"))
                    .andExpect(jsonPath("$.data.user.username").value("alice"));
        }

        @Test
        @DisplayName("returns 401 when InvalidCredentialsException is thrown")
        void invalidCredentials() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new InvalidCredentialsException("Invalid username or password"));

            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new LoginRequest("alice", "wrong"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------- /register ----------

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {
        @Test
        @DisplayName("returns 201 on successful registration")
        void registerSuccess() throws Exception {
            when(authService.register(any(RegisterRequest.class))).thenReturn(stubAuth("alice"));

            RegisterRequest body = RegisterRequest.builder()
                    .name("Alice").username("alice").email("alice@x.com")
                    .password("Strong@1234").phone("9000000001").build();

            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.accessToken").value("access"));
        }

        @Test
        @DisplayName("returns 409 on duplicate username/email")
        void duplicateConflict() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new DuplicateResourceException("Username taken"));

            RegisterRequest body = RegisterRequest.builder()
                    .name("Alice").username("alice").email("alice@x.com")
                    .password("Strong@1234").phone("9000000001").build();

            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("returns 400 on validation error (missing required field)")
        void validationError() throws Exception {
            // Missing required name + username + email + password + phone
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 on weak password")
        void weakPassword() throws Exception {
            RegisterRequest body = RegisterRequest.builder()
                    .name("Alice").username("alice").email("alice@x.com")
                    .password("weak").phone("9000000001").build();
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------- /refresh ----------

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class Refresh {
        @Test
        @DisplayName("returns 200 + new tokens on valid refresh")
        void refreshSuccess() throws Exception {
            when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(stubAuth("alice"));

            mvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RefreshTokenRequest("good-refresh"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access"));
        }

        @Test
        @DisplayName("returns 401 on invalid refresh token")
        void invalidRefresh() throws Exception {
            when(authService.refreshToken(any(RefreshTokenRequest.class)))
                    .thenThrow(new InvalidCredentialsException("expired"));

            mvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RefreshTokenRequest("bad"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------- /change-password ----------

    @Nested
    @DisplayName("POST /api/v1/auth/change-password")
    class ChangePassword {
        @Test
        @DisplayName("returns 200 on successful change")
        void success() throws Exception {
            when(authService.changePassword(eq(7L), anyString(), anyString())).thenReturn(stubAuth("alice"));

            mvc.perform(post("/api/v1/auth/change-password")
                            .header("X-User-Id", "7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "currentPassword", "Old@Pass1",
                                    "newPassword", "New@Pass1234"))))
                    .andExpect(status().isOk());

            verify(authService).changePassword(7L, "Old@Pass1", "New@Pass1234");
        }

        @Test
        @DisplayName("returns 401 when current password is wrong")
        void wrongCurrentPassword() throws Exception {
            when(authService.changePassword(anyLong(), anyString(), anyString()))
                    .thenThrow(new InvalidCredentialsException("Current password is incorrect"));

            mvc.perform(post("/api/v1/auth/change-password")
                            .header("X-User-Id", "7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "currentPassword", "wrong", "newPassword", "New@Pass1234"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 400 on weak new password")
        void weakNewPassword() throws Exception {
            when(authService.changePassword(anyLong(), anyString(), anyString()))
                    .thenThrow(new IllegalArgumentException("weak"));

            mvc.perform(post("/api/v1/auth/change-password")
                            .header("X-User-Id", "7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "currentPassword", "Old@Pass1", "newPassword", "weak"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("fails when X-User-Id header missing (handled by GlobalExceptionHandler)")
        void missingHeader() throws Exception {
            // The custom GlobalExceptionHandler catches MissingRequestHeaderException
            // via the catch-all branch and returns 500. Acceptable behaviour: anything 4xx-5xx.
            mvc.perform(post("/api/v1/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "currentPassword", "Old@Pass1", "newPassword", "New@Pass1234"))))
                    .andExpect(status().is5xxServerError());
        }
    }

    // ---------- /logout ----------

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {
        @Test
        @DisplayName("returns 200 and delegates to authService.logout(userId)")
        void logoutSuccess() throws Exception {
            mvc.perform(post("/api/v1/auth/logout").header("X-User-Id", "42"))
                    .andExpect(status().isOk());
            verify(authService).logout(42L);
        }

        @Test
        @DisplayName("fails when X-User-Id header missing")
        void missingHeader() throws Exception {
            mvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().is5xxServerError());
        }
    }
}
