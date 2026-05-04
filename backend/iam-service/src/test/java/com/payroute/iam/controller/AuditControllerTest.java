package com.payroute.iam.controller;

import com.payroute.iam.dto.response.AuditLogResponse;
import com.payroute.iam.dto.response.PagedResponse;
import com.payroute.iam.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AuditService auditService;

    @Test
    void getAuditLogs_returnsPagedResponse() throws Exception {
        PagedResponse<AuditLogResponse> page = PagedResponse.<AuditLogResponse>builder()
                .content(List.of(AuditLogResponse.builder().id(1L).action("LOGIN").build()))
                .page(0).size(20).totalElements(1).totalPages(1).last(true).build();
        when(auditService.getAuditLogs(any())).thenReturn(page);

        mvc.perform(get("/api/v1/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].action").value("LOGIN"));
    }

    @Test
    void getAuditLogsByUser_filtersOnUserId() throws Exception {
        PagedResponse<AuditLogResponse> empty = PagedResponse.<AuditLogResponse>builder()
                .content(List.of()).page(0).size(20).totalElements(0).totalPages(0).last(true).build();
        when(auditService.getAuditLogsByUser(eq(42L), any())).thenReturn(empty);

        mvc.perform(get("/api/v1/audit/user/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
