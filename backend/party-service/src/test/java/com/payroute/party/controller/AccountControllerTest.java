package com.payroute.party.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroute.party.dto.request.AccountRequest;
import com.payroute.party.dto.response.AccountResponse;
import com.payroute.party.dto.response.AccountValidationResponse;
import com.payroute.party.dto.response.PagedResponse;
import com.payroute.party.exception.DuplicateResourceException;
import com.payroute.party.exception.ResourceNotFoundException;
import com.payroute.party.service.AccountDirectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
class AccountControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean AccountDirectoryService accountService;

    private AccountResponse stub() {
        return AccountResponse.builder()
                .id(10L).accountNumber("1111222233334444").ifscIban("HDFC0001234")
                .currency("INR").accountType("SAVINGS").build();
    }

    private AccountRequest validReq() {
        return AccountRequest.builder()
                .partyId(1L).accountNumber("1111222233334444").ifscIban("HDFC0001234")
                .currency("INR").accountType("SAVINGS").build();
    }

    @Test
    void list_returnsPagedResponse() throws Exception {
        PagedResponse<AccountResponse> page = PagedResponse.<AccountResponse>builder()
                .content(List.of(stub())).page(0).size(20)
                .totalElements(1).totalPages(1).last(true).build();
        when(accountService.getAccounts(any(), any())).thenReturn(page);

        mvc.perform(get("/api/v1/accounts").param("partyId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].accountNumber").value("1111222233334444"));
    }

    @Test
    void create_returns201() throws Exception {
        when(accountService.createAccount(any(), any())).thenReturn(stub());
        mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validReq())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void create_validationFails_returns400() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns409_onDuplicate() throws Exception {
        when(accountService.createAccount(any(), any()))
                .thenThrow(new DuplicateResourceException("Account", "accountNumber", "1111222233334444"));
        mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validReq())))
                .andExpect(status().isConflict());
    }

    @Test
    void getById_found() throws Exception {
        when(accountService.getAccountById(10L)).thenReturn(stub());
        mvc.perform(get("/api/v1/accounts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void getById_notFound() throws Exception {
        when(accountService.getAccountById(99L))
                .thenThrow(new ResourceNotFoundException("Account", "id", 99L));
        mvc.perform(get("/api/v1/accounts/99")).andExpect(status().isNotFound());
    }

    @Test
    void update_returns200() throws Exception {
        when(accountService.updateAccount(eq(10L), any(), any())).thenReturn(stub());
        mvc.perform(put("/api/v1/accounts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validReq())))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete("/api/v1/accounts/10"))
                .andExpect(status().isNoContent());
        verify(accountService).deleteAccount(eq(10L), any());
    }

    @Test
    void validateAccount_returnsValidationResponse() throws Exception {
        when(accountService.validateAccount("1111222233334444", "HDFC0001234"))
                .thenReturn(AccountValidationResponse.builder()
                        .exists(true).active(true).currency("INR")
                        .partyName("Alice").accountId(10L).build());

        mvc.perform(get("/api/v1/accounts/validate")
                        .param("accountNumber", "1111222233334444")
                        .param("ifscIban", "HDFC0001234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.partyName").value("Alice"));
    }

    @Test
    void validateAccount_unknown_returnsExistsFalse() throws Exception {
        when(accountService.validateAccount(anyString(), anyString()))
                .thenReturn(AccountValidationResponse.builder().exists(false).active(false).build());

        mvc.perform(get("/api/v1/accounts/validate")
                        .param("accountNumber", "0000")
                        .param("ifscIban", "X"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false));
    }

    @Test
    void validateById_returnsValidationResponse() throws Exception {
        when(accountService.validateById(10L)).thenReturn(AccountValidationResponse.builder()
                .exists(true).active(true).accountId(10L).partyId(1L).build());
        mvc.perform(get("/api/v1/accounts/10/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partyId").value(1));
    }

    @Test
    void resolveByAlias_vpa() throws Exception {
        when(accountService.resolveByAlias("VPA", "alice@upi")).thenReturn(stub());
        mvc.perform(get("/api/v1/accounts/resolve")
                        .param("aliasType", "VPA").param("value", "alice@upi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void resolveByAlias_invalidType_returns400() throws Exception {
        when(accountService.resolveByAlias("BANK_CODE", "x"))
                .thenThrow(new IllegalArgumentException("Unsupported aliasType"));
        mvc.perform(get("/api/v1/accounts/resolve")
                        .param("aliasType", "BANK_CODE").param("value", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolveByAlias_notFound_returns404() throws Exception {
        when(accountService.resolveByAlias(anyString(), anyString()))
                .thenThrow(new ResourceNotFoundException("Account", "vpa", "ghost@upi"));
        mvc.perform(get("/api/v1/accounts/resolve")
                        .param("aliasType", "VPA").param("value", "ghost@upi"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lookup_returnsAccount() throws Exception {
        when(accountService.findByAlias("alice-main")).thenReturn(stub());
        mvc.perform(get("/api/v1/accounts/lookup").param("alias", "alice-main"))
                .andExpect(status().isOk());
    }
}
