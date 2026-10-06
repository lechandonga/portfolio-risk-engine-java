package com.github.lechandonga.portfolioriskenginejava.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("业务6：REST 接口链路与 HTTP 错误语义")
class WebApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("下单→重试幂等→查询估值→未知标的404 全链路可用")
    void fullFlowOverHttp() throws Exception {
        String body = """
                {"clientRequestId":"web-1","symbol":"AAPL","side":"BUY",
                 "type":"MARKET","quantity":100}
                """;
        mockMvc.perform(post("/api/accounts/demo/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.fee").exists());

        mockMvc.perform(post("/api/accounts/demo/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FILLED"));

        mockMvc.perform(get("/api/accounts/demo/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.risk.status").value("OK"))
                .andExpect(jsonPath("$.positions[0].symbol").exists());

        mockMvc.perform(post("/api/accounts/demo/orders")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"clientRequestId":"web-2","symbol":"NOPE","side":"BUY",
                                 "type":"MARKET","quantity":1}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SYMBOL_NOT_FOUND"));

        mockMvc.perform(get("/api/accounts/demo/portfolio?confidence=1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RISK_INVALID_CONFIDENCE"));

        mockMvc.perform(get("/api/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolios[0].accountId").exists());
    }
}
