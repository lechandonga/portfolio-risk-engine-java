package com.github.lechandonga.portfolioriskenginejava.api;

import com.github.lechandonga.portfolioriskenginejava.risk.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.risk.RiskConfidence;
import com.github.lechandonga.portfolioriskenginejava.risk.ValuationService;
import com.github.lechandonga.portfolioriskenginejava.store.AccountStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
public class PortfolioController {

    private final ValuationService valuationService;
    private final RiskConfidence defaultConfidence;
    private final AccountStore store;

    public PortfolioController(ValuationService valuationService,
                               RiskConfidence defaultConfidence,
                               AccountStore store) {
        this.valuationService = valuationService;
        this.defaultConfidence = defaultConfidence;
        this.store = store;
    }

    @GetMapping("/api/accounts/{accountId}/portfolio")
    public PortfolioValuation getPortfolio(
            @PathVariable String accountId,
            @RequestParam(required = false) BigDecimal confidence) {
        RiskConfidence rc = confidence == null
                ? defaultConfidence
                : new RiskConfidence(confidence, defaultConfidence.minHistorySamples());
        return valuationService.evaluate(accountId, rc);
    }

    /** 批量估值：逐账户隔离，失败账户放在 failures 中，不影响其他账户。 */
    @GetMapping("/api/portfolios")
    public BatchResult getAll(
            @RequestParam(required = false) BigDecimal confidence) {
        RiskConfidence rc = confidence == null
                ? defaultConfidence
                : new RiskConfidence(confidence, defaultConfidence.minHistorySamples());
        List<AccountStore.AccountFailure> failures = new ArrayList<>();
        List<PortfolioValuation> valuations = valuationService.evaluateAll(rc, failures);
        List<FailureView> failureViews = failures.stream()
                .map(f -> new FailureView(f.accountId(),
                        f.cause().getClass().getSimpleName(),
                        f.cause().getMessage()))
                .toList();
        return new BatchResult(valuations, failureViews);
    }

    public record BatchResult(List<PortfolioValuation> portfolios,
                              List<FailureView> failures) {
    }

    public record FailureView(String accountId, String type, String message) {
    }
}
