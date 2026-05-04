package com.payroute.party.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroute.party.dto.request.PartyRequest;
import com.payroute.party.dto.response.PagedResponse;
import com.payroute.party.dto.response.PartyResponse;
import com.payroute.party.entity.PartyStatus;
import com.payroute.party.entity.PartyType;
import com.payroute.party.exception.ResourceNotFoundException;
import com.payroute.party.service.PartyService;
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
class PartyControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean PartyService partyService;

    private PartyResponse stub() {
        return PartyResponse.builder()
                .id(1L).name("Acme").type(PartyType.CORPORATE)
                .country("IND").status(PartyStatus.ACTIVE).build();
    }

    @Test
    void list_returnsPagedResponse() throws Exception {
        PagedResponse<PartyResponse> page = PagedResponse.<PartyResponse>builder()
                .content(List.of(stub())).page(0).size(20)
                .totalElements(1).totalPages(1).last(true).build();
        when(partyService.getAllParties(any(), any(), any())).thenReturn(page);

        mvc.perform(get("/api/v1/parties").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Acme"));
    }

    @Test
    void list_filtersByStatusAndType() throws Exception {
        PagedResponse<PartyResponse> page = PagedResponse.<PartyResponse>builder()
                .content(List.of(stub())).page(0).size(20)
                .totalElements(1).totalPages(1).last(true).build();
        when(partyService.getAllParties(eq(PartyStatus.ACTIVE), eq(PartyType.CORPORATE), any())).thenReturn(page);

        mvc.perform(get("/api/v1/parties").param("status", "ACTIVE").param("type", "CORPORATE"))
                .andExpect(status().isOk());
        verify(partyService).getAllParties(eq(PartyStatus.ACTIVE), eq(PartyType.CORPORATE), any());
    }

    @Test
    void getById_found() throws Exception {
        when(partyService.getPartyById(1L)).thenReturn(stub());
        mvc.perform(get("/api/v1/parties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme"));
    }

    @Test
    void getById_notFound() throws Exception {
        when(partyService.getPartyById(99L)).thenThrow(new ResourceNotFoundException("Party", "id", 99L));
        mvc.perform(get("/api/v1/parties/99")).andExpect(status().isNotFound());
    }

    @Test
    void create_returns201() throws Exception {
        PartyRequest req = PartyRequest.builder().name("Acme").type(PartyType.CORPORATE).country("IND").build();
        when(partyService.createParty(any())).thenReturn(stub());

        mvc.perform(post("/api/v1/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void create_validationFails_returns400() throws Exception {
        // missing required name + type
        mvc.perform(post("/api/v1/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns200() throws Exception {
        PartyRequest req = PartyRequest.builder().name("Acme V2").type(PartyType.CORPORATE).country("IND").build();
        when(partyService.updateParty(eq(1L), any())).thenReturn(stub());
        mvc.perform(put("/api/v1/parties/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns204() throws Exception {
        mvc.perform(delete("/api/v1/parties/1"))
                .andExpect(status().isNoContent());
        verify(partyService).deleteParty(1L);
    }

    @Test
    void delete_notFound() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Party", "id", 99L))
                .when(partyService).deleteParty(99L);
        mvc.perform(delete("/api/v1/parties/99"))
                .andExpect(status().isNotFound());
    }
}
