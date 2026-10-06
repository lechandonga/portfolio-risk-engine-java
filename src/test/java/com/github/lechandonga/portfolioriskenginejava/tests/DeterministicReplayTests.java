package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.service.PortfolioBatchService;
import com.github.lechandonga.portfolioriskenginejava.tests.support.BusinessTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Replay determinism via the seeded REST API: the same request sequence against the
 * fixed seed (deterministic histories, controllable clock) returns identical numbers.
 */
@org.springframework.boot.test.context.SpringBootTest(webEnvironment =
        org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeterministicReplayTests extends BusinessTestBase {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired PortfolioBatchService batch;

    @Override
    protected String businessModule() {
        return "确定性回放-相同输入与行情得到相同结果";
    }

    private PortfolioValuation replayOnce(String runId) throws Exception {
        String base = "http://localhost:" + port;
        String accountJson = """
                {"accountId":"%s","name":"replay","initialCash":"100000"}
                """.formatted(runId);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.postForEntity(base + "/api/accounts", new HttpEntity<>(accountJson, headers), String.class);

        String orderJson = """
                {"clientRequestId":"o1","side":"BUY","symbol":"AAA","quantity":1000}
                """;
        var order = rest.postForEntity(base + "/api/accounts/" + runId + "/orders",
                new HttpEntity<>(orderJson, headers), String.class);
        assertEquals(200, order.getStatusCode().value(), order.getBody());

        String orderId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(order.getBody()).get("orderId").asText();
        rest.exchange(base + "/api/accounts/" + runId + "/orders/" + orderId + "/fill",
                HttpMethod.POST, new HttpEntity<>("", headers), String.class);

        var response = rest.getForEntity(base + "/api/accounts/" + runId + "/valuation",
                PortfolioValuation.class);
        assertEquals(200, response.getStatusCode().value());
        return response.getBody();
    }

    @Test
    @DisplayName("两次独立回放: 现金、市值、未实现盈亏完全一致")
    void sameInputsSameResults() throws Exception {
        PortfolioValuation first = replayOnce("replay-a");
        PortfolioValuation second = replayOnce("replay-b");
        assertEquals(first.cash(), second.cash());
        assertEquals(first.totalMarketValue(), second.totalMarketValue());
        assertEquals(first.totalUnrealizedPnl(), second.totalUnrealizedPnl());
        assertEquals(first.positions().size(), second.positions().size());
        // All accounts (including both replays) are serviced independently in the batch view.
        assertEquals(0, batch.valueAll().values().stream()
                .filter(r -> r.errorCode() != null).count());
        log.info("回放一致: runA 现金={} 市值={} | runB 现金={} 市值={}",
                first.cash(), first.totalMarketValue(), second.cash(), second.totalMarketValue());
    }
}
