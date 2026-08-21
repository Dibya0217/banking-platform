package com.banking.admin.controller;

import com.banking.admin.service.AdminProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@EnableMethodSecurity
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminProxyService adminProxyService;

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getCustomers_withAdminRole_returns200() throws Exception {
        when(adminProxyService.getCustomers(any(), any(), any()))
                .thenReturn(Mono.just("{\"content\":[],\"totalElements\":0}"));

        mockMvc.perform(get("/api/v1/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"content\":[],\"totalElements\":0}"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CUSTOMER")
    void getCustomers_withoutAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/customers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void freezeCustomer_withAdminRole_callsProxy() throws Exception {
        when(adminProxyService.freezeCustomer(any(), any()))
                .thenReturn(Mono.just("{\"success\":true}"));

        mockMvc.perform(post("/api/v1/admin/customers/cust-123/freeze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"suspicious activity\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"success\":true}"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getFraudAlerts_withAdminRole_returns200() throws Exception {
        when(adminProxyService.getFraudAlerts(any(), any(), any()))
                .thenReturn(Mono.just("{\"alerts\":[]}"));

        mockMvc.perform(get("/api/v1/admin/fraud/alerts"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"alerts\":[]}"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void getAccounts_withAdminRole_returns200() throws Exception {
        when(adminProxyService.getAccounts(any(), any(), any()))
                .thenReturn(Mono.just("{\"accounts\":[]}"));

        mockMvc.perform(get("/api/v1/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"accounts\":[]}"));
    }
}
