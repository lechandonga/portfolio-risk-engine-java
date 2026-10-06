package com.github.lechandonga.portfolioriskenginejava.web;

import com.github.lechandonga.portfolioriskenginejava.dto.Responses.RiskReport;
import com.github.lechandonga.portfolioriskenginejava.service.PortfolioBatchService;
import com.github.lechandonga.portfolioriskenginejava.service.RiskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RiskController {
    private final RiskService risk;
    private final PortfolioBatchService batch;

    public RiskController(RiskService risk, PortfolioBatchService batch) {
        this.risk = risk;
        this.batch = batch;
    }

    @GetMapping("/accounts/{accountId}/risk")
    public RiskReport report(@PathVariable String accountId,
                             @RequestParam(required = false) BigDecimal confidence) {
        return risk.report(accountId, confidence);
    }

    @GetMapping("/risks")
    public Map<String, PortfolioBatchService.AccountResult<RiskReport>> reportAll(
            @RequestParam(required = false) BigDecimal confidence) {
        return batch.riskAll(confidence);
    }
}
