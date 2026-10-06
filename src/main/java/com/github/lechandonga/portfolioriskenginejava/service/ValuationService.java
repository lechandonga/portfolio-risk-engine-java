package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.Position;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.PositionValuation;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.ValuationStatus;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataService;
import com.github.lechandonga.portfolioriskenginejava.market.Quote;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Mark-to-market valuation. Missing/stale/dirty quotes never fall back to a default or
 * last-known-good price: the affected line is marked MARKET_DATA_UNAVAILABLE and
 * excluded from totals, while clean positions still value normally.
 */
@Service
public class ValuationService {
    private final AccountRepository accounts;
    private final PositionRepository positions;
    private final MarketDataService market;

    public ValuationService(AccountRepository accounts, PositionRepository positions,
                            MarketDataService market) {
        this.accounts = accounts;
        this.positions = positions;
        this.market = market;
    }

    public PortfolioValuation value(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + accountId);
        }
        List<PositionValuation> lines = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalUnrealized = BigDecimal.ZERO;

        for (Position position : positions.findByAccount(accountId)) {
            if (position.getQuantity() == 0) {
                // Zero holding is a genuine zero, not "uncomputable".
                lines.add(new PositionValuation(position.getSymbol(), 0, 0,
                        position.averageCost(), position.getCostBasis(), null,
                        BigDecimal.ZERO, BigDecimal.ZERO, ValuationStatus.OK, null));
                continue;
            }
            Quote quote = market.tryFreshQuote(position.getSymbol());
            if (quote == null) {
                unavailable.add(position.getSymbol());
                lines.add(new PositionValuation(position.getSymbol(), position.getQuantity(),
                        position.availableQuantity(), position.averageCost(), position.getCostBasis(),
                        null, null, null, ValuationStatus.MARKET_DATA_UNAVAILABLE,
                        "no fresh quote; position excluded from totals"));
                continue;
            }
            BigDecimal marketValue = quote.price().multiply(BigDecimal.valueOf(position.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealized = marketValue.subtract(position.getCostBasis());
            totalMarketValue = totalMarketValue.add(marketValue);
            totalUnrealized = totalUnrealized.add(unrealized);
            lines.add(new PositionValuation(position.getSymbol(), position.getQuantity(),
                    position.availableQuantity(), position.averageCost(), position.getCostBasis(),
                    quote.price(), marketValue, unrealized, ValuationStatus.OK, null));
        }

        BigDecimal equity = account.getCash().add(totalMarketValue);
        return new PortfolioValuation(accountId, account.getCash(), account.getReservedCash(),
                totalMarketValue, totalUnrealized, account.getRealizedPnl(), equity,
                List.copyOf(lines), List.copyOf(unavailable));
    }
}
