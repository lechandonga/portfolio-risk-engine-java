package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.dto.Responses.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.RiskReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fan-out over accounts. One account failing (bad market data, corrupt state during a test)
 * is captured as an error entry and never aborts the other accounts.
 */
@Service
public class PortfolioBatchService {
    private static final Logger log = LoggerFactory.getLogger(PortfolioBatchService.class);

    private final AccountRepository accounts;
    private final ValuationService valuation;
    private final RiskService risk;

    public PortfolioBatchService(AccountRepository accounts, ValuationService valuation, RiskService risk) {
        this.accounts = accounts;
        this.valuation = valuation;
        this.risk = risk;
    }

    public record AccountResult<T>(String accountId, T result, String errorCode, String errorMessage) {}

    public Map<String, AccountResult<PortfolioValuation>> valueAll() {
        Map<String, AccountResult<PortfolioValuation>> out = new LinkedHashMap<>();
        for (var account : accounts.findAll()) {
            try {
                out.put(account.getAccountId(),
                        new AccountResult<>(account.getAccountId(), valuation.value(account.getAccountId()), null, null));
            } catch (RuntimeException ex) {
                log.warn("valuation failed for {}: {}", account.getAccountId(), ex.toString());
                out.put(account.getAccountId(), new AccountResult<>(account.getAccountId(), null,
                        ex.getClass().getSimpleName(), ex.getMessage()));
            }
        }
        return out;
    }

    public Map<String, AccountResult<RiskReport>> riskAll(BigDecimal confidence) {
        Map<String, AccountResult<RiskReport>> out = new LinkedHashMap<>();
        for (var account : accounts.findAll()) {
            try {
                out.put(account.getAccountId(),
                        new AccountResult<>(account.getAccountId(),
                                risk.report(account.getAccountId(), confidence), null, null));
            } catch (RuntimeException ex) {
                log.warn("risk report failed for {}: {}", account.getAccountId(), ex.toString());
                out.put(account.getAccountId(), new AccountResult<>(account.getAccountId(), null,
                        ex.getClass().getSimpleName(), ex.getMessage()));
            }
        }
        return out;
    }
}
