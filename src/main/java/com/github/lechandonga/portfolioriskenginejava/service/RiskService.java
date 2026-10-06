package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.config.EngineProperties;
import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.Position;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.PositionRisk;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.RiskReport;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.RiskStatus;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataService;
import com.github.lechandonga.portfolioriskenginejava.market.Quote;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Historical-simulation risk model, deliberately simple and explainable:
 *  - daily returns r_i = P_i/P_{i-1} - 1
 *  - VaR_c    = marketValue * quantile_c(-r): the loss threshold exceeded in (1-c) of days
 *  - ES_c     = marketValue * average of losses at or beyond that threshold
 *  - volatility = sample standard deviation of r_i
 *  - portfolio VaR/ES = sum of component VaRs (no cross-asset diversification assumed)
 *
 * Insufficient history or a missing quote => NOT_COMPUTABLE, never a fabricated zero.
 */
@Service
public class RiskService {
    private final AccountRepository accounts;
    private final PositionRepository positions;
    private final MarketDataService market;
    private final EngineProperties props;

    public RiskService(AccountRepository accounts, PositionRepository positions,
                       MarketDataService market, EngineProperties props) {
        this.accounts = accounts;
        this.positions = positions;
        this.market = market;
        this.props = props;
    }

    public RiskReport report(String accountId, BigDecimal confidenceOverride) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + accountId);
        }
        BigDecimal confidence = confidenceOverride != null ? confidenceOverride : props.getRisk().getConfidence();
        if (confidence.compareTo(props.getRisk().getMinConfidence()) < 0
                || confidence.compareTo(props.getRisk().getMaxConfidence()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "confidence must be within [" + props.getRisk().getMinConfidence() + ", "
                            + props.getRisk().getMaxConfidence() + "]");
        }

        List<PositionRisk> lines = new ArrayList<>();
        List<String> notComputable = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalVar = BigDecimal.ZERO;
        BigDecimal totalEs = BigDecimal.ZERO;

        for (Position position : positions.findByAccount(accountId)) {
            if (position.getQuantity() <= 0) {
                lines.add(new PositionRisk(position.getSymbol(), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, RiskStatus.OK, null));
                continue;
            }
            Quote quote = market.tryFreshQuote(position.getSymbol());
            if (quote == null) {
                notComputable.add(position.getSymbol());
                lines.add(new PositionRisk(position.getSymbol(), null, null, null, null, null,
                        RiskStatus.NOT_COMPUTABLE, "no fresh quote"));
                continue;
            }
            BigDecimal marketValue = quote.price().multiply(BigDecimal.valueOf(position.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            List<BigDecimal> history;
            try {
                history = market.requireHistory(position.getSymbol(), props.getRisk().getHistoryPoints());
            } catch (BusinessException ex) {
                notComputable.add(position.getSymbol());
                lines.add(new PositionRisk(position.getSymbol(), marketValue, null, null, null, null,
                        RiskStatus.NOT_COMPUTABLE, "price history unavailable: " + ex.getCode()));
                continue;
            }
            List<Double> returns = dailyReturns(history);
            int tailCount = (int) Math.ceil((1.0 - confidence.doubleValue()) * returns.size());
            if (returns.size() < props.getRisk().getMinReturnPoints() || tailCount < 1
                    || tailCount > returns.size()) {
                notComputable.add(position.getSymbol());
                lines.add(new PositionRisk(position.getSymbol(), marketValue, null, null, null, null,
                        RiskStatus.NOT_COMPUTABLE,
                        "insufficient history (" + history.size() + " prices / " + returns.size()
                                + " returns, need >= " + props.getRisk().getMinReturnPoints()
                                + ") for confidence " + confidence));
                continue;
            }
            List<Double> losses = returns.stream().map(r -> -r).sorted(Collections.reverseOrder()).toList();
            double varRate = losses.get(tailCount - 1);
            double esRate = losses.subList(0, tailCount).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);
            double vol = stdev(returns);

            BigDecimal var = BigDecimal.valueOf(Math.max(0.0, varRate) * marketValue.doubleValue())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal es = BigDecimal.valueOf(Math.max(0.0, esRate) * marketValue.doubleValue())
                    .setScale(2, RoundingMode.HALF_UP);
            totalValue = totalValue.add(marketValue);
            totalVar = totalVar.add(var);
            totalEs = totalEs.add(es);
            lines.add(new PositionRisk(position.getSymbol(), marketValue, null,
                    BigDecimal.valueOf(vol).setScale(6, RoundingMode.HALF_UP), var, es,
                    RiskStatus.OK, null));
        }

        if (totalValue.signum() > 0) {
            List<PositionRisk> weighted = new ArrayList<>(lines.size());
            for (PositionRisk line : lines) {
                if (line.status() == RiskStatus.OK && line.marketValue() != null
                        && line.marketValue().signum() > 0) {
                    BigDecimal weight = line.marketValue().divide(totalValue, 6, RoundingMode.HALF_UP);
                    weighted.add(new PositionRisk(line.symbol(), line.marketValue(), weight,
                            line.dailyVolatility(), line.var(), line.expectedShortfall(),
                            line.status(), line.reason()));
                } else {
                    weighted.add(line);
                }
            }
            lines = weighted;
        }
        return new RiskReport(accountId, confidence, totalValue, totalVar, totalEs,
                List.copyOf(lines), List.copyOf(notComputable));
    }

    private static List<Double> dailyReturns(List<BigDecimal> history) {
        List<Double> returns = new ArrayList<>(Math.max(0, history.size() - 1));
        for (int i = 1; i < history.size(); i++) {
            double prev = history.get(i - 1).doubleValue();
            double curr = history.get(i).doubleValue();
            if (prev > 0) {
                returns.add(curr / prev - 1.0);
            }
        }
        return returns;
    }

    private static double stdev(List<Double> values) {
        if (values.size() < 2) {
            return 0.0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sum = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return Math.sqrt(sum / (values.size() - 1));
    }
}
