package com.github.lechandonga.portfolioriskenginejava.web;

import com.github.lechandonga.portfolioriskenginejava.dto.Responses.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.service.PortfolioBatchService;
import com.github.lechandonga.portfolioriskenginejava.service.ValuationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ValuationController {
    private final ValuationService valuation;
    private final PortfolioBatchService batch;

    public ValuationController(ValuationService valuation, PortfolioBatchService batch) {
        this.valuation = valuation;
        this.batch = batch;
    }

    @GetMapping("/accounts/{accountId}/valuation")
    public PortfolioValuation value(@PathVariable String accountId) {
        return valuation.value(accountId);
    }

    @GetMapping("/valuations")
    public Map<String, PortfolioBatchService.AccountResult<PortfolioValuation>> valueAll() {
        return batch.valueAll();
    }
}
