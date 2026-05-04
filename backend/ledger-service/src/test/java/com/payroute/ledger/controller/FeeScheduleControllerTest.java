package com.payroute.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroute.ledger.dto.request.FeeScheduleRequest;
import com.payroute.ledger.dto.response.FeeScheduleResponse;
import com.payroute.ledger.entity.FeeType;
import com.payroute.ledger.entity.RailType;
import com.payroute.ledger.exception.ResourceNotFoundException;
import com.payroute.ledger.service.FeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class FeeScheduleControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean FeeService feeService;

    private FeeScheduleResponse stub() {
        return FeeScheduleResponse.builder()
                .id(1L).product("PAYMENT").rail(RailType.NEFT)
                .feeType(FeeType.PERCENTAGE).value(new BigDecimal("0.25"))
                .currency("INR").build();
    }

    private FeeScheduleRequest validReq() {
        return FeeScheduleRequest.builder()
                .product("PAYMENT").rail(RailType.NEFT)
                .feeType(FeeType.PERCENTAGE).value(new BigDecimal("0.25"))
                .currency("INR").effectiveFrom(LocalDate.of(2026, 1, 1)).build();
    }

    @Test
    void create_returns201() throws Exception {
        when(feeService.createFeeSchedule(any())).thenReturn(stub());
        mvc.perform(post("/api/v1/ledger/fees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validReq())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.product").value("PAYMENT"));
    }

    @Test
    void create_validationFails_returns400() throws Exception {
        mvc.perform(post("/api/v1/ledger/fees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_found() throws Exception {
        when(feeService.getFeeScheduleById(1L)).thenReturn(stub());
        mvc.perform(get("/api/v1/ledger/fees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getById_notFound() throws Exception {
        when(feeService.getFeeScheduleById(99L))
                .thenThrow(new ResourceNotFoundException("FeeSchedule", "id", 99L));
        mvc.perform(get("/api/v1/ledger/fees/99")).andExpect(status().isNotFound());
    }

    @Test
    void getAll_returnsList() throws Exception {
        when(feeService.getAllFeeSchedules()).thenReturn(List.of(stub()));
        mvc.perform(get("/api/v1/ledger/fees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getActive_returnsList() throws Exception {
        when(feeService.getActiveFeeSchedules()).thenReturn(List.of(stub()));
        mvc.perform(get("/api/v1/ledger/fees/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rail").value("NEFT"));
    }

    @Test
    void update_returns200() throws Exception {
        when(feeService.updateFeeSchedule(eq(1L), any())).thenReturn(stub());
        mvc.perform(put("/api/v1/ledger/fees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validReq())))
                .andExpect(status().isOk());
    }

    @Test
    void deactivate_returns200() throws Exception {
        mvc.perform(delete("/api/v1/ledger/fees/1"))
                .andExpect(status().isOk());
        verify(feeService).deactivateFeeSchedule(1L);
    }
}
