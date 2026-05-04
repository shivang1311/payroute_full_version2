package com.payroute.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroute.iam.dto.response.PagedResponse;
import com.payroute.iam.dto.response.UserResponse;
import com.payroute.iam.entity.Role;
import com.payroute.iam.exception.DuplicateResourceException;
import com.payroute.iam.exception.InvalidCredentialsException;
import com.payroute.iam.exception.ResourceNotFoundException;
import com.payroute.iam.service.UserService;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean UserService userService;

    private UserResponse stub(long id, String username, Role role) {
        return UserResponse.builder()
                .id(id).username(username).email(username + "@x.com").phone("9000000099")
                .role(role).active(true).pinSet(true)
                .createdAt(LocalDateTime.now()).build();
    }

    // -------------------- GET /api/v1/users --------------------

    @Nested
    @DisplayName("GET /api/v1/users (list)")
    class List_ {
        @Test
        void returnsPagedResponse() throws Exception {
            PagedResponse<UserResponse> page = PagedResponse.<UserResponse>builder()
                    .content(List.of(stub(1L, "alice", Role.CUSTOMER)))
                    .page(0).size(20).totalElements(1).totalPages(1).last(true).build();
            when(userService.getAllUsers(any())).thenReturn(page);

            mvc.perform(get("/api/v1/users").param("page", "0").param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].username").value("alice"));
        }
    }

    // -------------------- GET /me --------------------

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetMe {
        @Test
        void returnsCallerProfile() throws Exception {
            when(userService.getUserById(7L)).thenReturn(stub(7L, "alice", Role.CUSTOMER));
            mvc.perform(get("/api/v1/users/me").header("X-User-Id", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(7));
        }
    }

    // -------------------- GET /{id} --------------------

    @Nested
    @DisplayName("GET /api/v1/users/{id}")
    class GetById {
        @Test
        void found() throws Exception {
            when(userService.getUserById(1L)).thenReturn(stub(1L, "alice", Role.CUSTOMER));
            mvc.perform(get("/api/v1/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("alice"));
        }

        @Test
        void notFound() throws Exception {
            when(userService.getUserById(99L)).thenThrow(new ResourceNotFoundException("nope"));
            mvc.perform(get("/api/v1/users/99")).andExpect(status().isNotFound());
        }
    }

    // -------------------- POST /staff (admin only) --------------------

    @Nested
    @DisplayName("POST /api/v1/users/staff")
    class CreateStaff {
        @Test
        @DisplayName("returns 201 when caller is ADMIN")
        void asAdmin() throws Exception {
            when(userService.createStaffUser(anyString(), anyString(), anyString(), anyString(), eq(Role.OPERATIONS)))
                    .thenReturn(stub(99L, "ops.bob", Role.OPERATIONS));

            mvc.perform(post("/api/v1/users/staff")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "username", "ops.bob",
                                    "email", "ops.bob@x.com",
                                    "phone", "9000000099",
                                    "password", "Welcome@1",
                                    "role", "OPERATIONS"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.username").value("ops.bob"));
        }

        @Test
        @DisplayName("returns 403 when caller is not ADMIN")
        void nonAdminBlocked() throws Exception {
            mvc.perform(post("/api/v1/users/staff")
                            .header("X-User-Role", "CUSTOMER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "username", "ops.bob",
                                    "email", "ops.bob@x.com",
                                    "phone", "9000000099",
                                    "password", "Welcome@1",
                                    "role", "OPERATIONS"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 400 on invalid role string")
        void invalidRole() throws Exception {
            mvc.perform(post("/api/v1/users/staff")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "username", "x",
                                    "email", "x@x.com",
                                    "phone", "9000000099",
                                    "password", "Welcome@1",
                                    "role", "GHOST"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 409 on duplicate")
        void duplicate() throws Exception {
            when(userService.createStaffUser(anyString(), anyString(), anyString(), anyString(), any()))
                    .thenThrow(new DuplicateResourceException("dup"));

            mvc.perform(post("/api/v1/users/staff")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "username", "ops.bob",
                                    "email", "ops.bob@x.com",
                                    "phone", "9000000099",
                                    "password", "Welcome@1",
                                    "role", "OPERATIONS"))))
                    .andExpect(status().isConflict());
        }
    }

    // -------------------- PUT /{id}/role --------------------

    @Nested
    @DisplayName("PUT /api/v1/users/{id}/role")
    class UpdateRole {
        @Test
        void success() throws Exception {
            when(userService.updateUserRole(1L, Role.OPERATIONS)).thenReturn(stub(1L, "alice", Role.OPERATIONS));

            mvc.perform(put("/api/v1/users/1/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("role", "OPERATIONS"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("OPERATIONS"));
        }
    }

    // -------------------- DELETE /{id} --------------------

    @Nested
    @DisplayName("DELETE /api/v1/users/{id}")
    class Deactivate {
        @Test
        void returns204() throws Exception {
            mvc.perform(delete("/api/v1/users/1"))
                    .andExpect(status().isNoContent());
            verify(userService).deactivateUser(1L);
        }
    }

    // -------------------- PIN endpoints --------------------

    @Nested
    @DisplayName("POST /api/v1/users/me/pin (set/change)")
    class SetPin {
        @Test
        void firstTimeSet() throws Exception {
            mvc.perform(post("/api/v1/users/me/pin")
                            .header("X-User-Id", "7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("pin", "1234"))))
                    .andExpect(status().isOk());
            verify(userService).setOwnTransactionPin(7L, "1234", null);
        }

        @Test
        @DisplayName("returns 400 on bad PIN format")
        void badFormat() throws Exception {
            doThrow(new IllegalArgumentException("pin format"))
                    .when(userService).setOwnTransactionPin(anyLong(), anyString(), any());

            mvc.perform(post("/api/v1/users/me/pin")
                            .header("X-User-Id", "7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("pin", "12"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when changing PIN with wrong password")
        void wrongPasswordOnChange() throws Exception {
            doThrow(new InvalidCredentialsException("Current password is incorrect"))
                    .when(userService).setOwnTransactionPin(anyLong(), anyString(), anyString());

            mvc.perform(post("/api/v1/users/me/pin")
                            .header("X-User-Id", "7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "pin", "5678",
                                    "currentPassword", "wrong"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/me/pin/verify")
    class VerifyOwnPin {
        @Test
        void valid() throws Exception {
            when(userService.verifyTransactionPin(7L, "1234")).thenReturn(true);
            mvc.perform(post("/api/v1/users/me/pin/verify")
                            .header("X-User-Id", "7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("pin", "1234"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(true));
        }

        @Test
        void invalid() throws Exception {
            when(userService.verifyTransactionPin(7L, "9999")).thenReturn(false);
            mvc.perform(post("/api/v1/users/me/pin/verify")
                            .header("X-User-Id", "7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("pin", "9999"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/{id}/pin/verify (service-to-service)")
    class VerifyById {
        @Test
        void serviceToService() throws Exception {
            when(userService.verifyTransactionPin(7L, "1234")).thenReturn(true);
            mvc.perform(post("/api/v1/users/7/pin/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("pin", "1234"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(true));
        }
    }

    // -------------------- PUT /me (update profile) --------------------

    @Nested
    @DisplayName("PUT /api/v1/users/me")
    class UpdateMe {
        @Test
        void success() throws Exception {
            UserResponse updated = stub(7L, "alice", Role.CUSTOMER);
            updated.setEmail("new@x.com");
            when(userService.updateOwnProfile(eq(7L), eq("new@x.com"), eq("9999999999")))
                    .thenReturn(updated);

            mvc.perform(put("/api/v1/users/me")
                            .header("X-User-Id", "7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "email", "new@x.com",
                                    "phone", "9999999999"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("new@x.com"));
        }
    }
}
