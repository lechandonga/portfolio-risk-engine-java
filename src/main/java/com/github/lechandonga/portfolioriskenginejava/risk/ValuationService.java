package com.github.lechandonga.portfolioriskenginejava.risk;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.Position;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ServiceException;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataException;
import com.github.lechandonga.portfolioriskenginejava.market.Quote;
import com.github.lechandonga.portfolioriskenginejava.market.ValidatingMarketDataProvider;
import com.github.lechandonga.portfolioriskenginejava.money.Money;
import com.github.lechandonga.portfolioriskenginejava.store.AccountEntry;
import com.github.lechandonga.portfolioriskenginejava.store.AccountStore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 估值与风险服务。
 *
 * 估值假设：
 * - 市值 = 持仓数量 × 最新有效收盘价；缺行情（含过期、脏数据）→ IMPOSSIBLE，不使用默认价；
 * - 未实现盈亏 = 市值 − 剩余持仓成本（买入含费）；已实现盈亏在成交时累计（卖出扣费）；
 * - 总权益 = 现金 + 总市值；任一持仓无法估值时总市值/总权益同样为 IMPOSSIBLE。
 *
 * 风险模型：历史模拟法（无任何分布假设、无外部数据）。取各持仓共同长度的历史价格，
 * 对相邻价格做简单收益率 r_t = p_t/p_{t-1} − 1，组合在当前权重下逐日历史损益，
 * 取损益分布的 (1−confidence) 分位数；VaR 取该分位损失的非负金额。
 * 样本不足（少于配置阈值）→ RISK_HISTORY_INSUFFICIENT，而不是返回 0。
 */
public class ValuationService {

    private final AccountStore store;
    private final ValidatingMarketDataProvider marketData;

    public ValuationService(AccountStore store, ValidatingMarketDataProvider marketData) {
        this.store = store;
        this.marketData = marketData;
    }

    public PortfolioValuation evaluate(String accountId, RiskConfidence confidence) {
        validateConfidence(confidence);
        return store.withAccount(accountId, entry -> build(entry, confidence));
    }

    /** 批量评估：每个账户独立计算，单账户失败被收集，不影响其他账户。 */
    public List<PortfolioValuation> evaluateAll(RiskConfidence confidence,
                                                List<AccountStore.AccountFailure> failures) {
        validateConfidence(confidence);
        List<PortfolioValuation> result = new ArrayList<>();
        store.forEachAccountIsolated(entry -> result.add(build(entry, confidence)),
                failures::add);
        return result;
    }

    private PortfolioValuation build(AccountEntry entry, RiskConfidence confidence) {
        Account account = entry.getAccount();
        List<PositionValuation> valuations = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO.setScale(Money.MONEY_SCALE);
        BigDecimal totalCost = BigDecimal.ZERO.setScale(Money.MONEY_SCALE);
        BigDecimal totalUnrealized = BigDecimal.ZERO.setScale(Money.MONEY_SCALE);
        boolean anyImpossible = false;
        List<String> impossibleReasons = new ArrayList<>();

        for (Position position : new ArrayList<>(account.getPositions().values())) {
            if (position.getQuantity().signum() == 0) {
                continue;
            }
            PositionValuation valuation = valuePosition(position);
            valuations.add(valuation);
            totalCost = totalCost.add(position.getCostBasis());
            if (valuation.status() == ValuationStatus.IMPOSSIBLE) {
                anyImpossible = true;
                impossibleReasons.add(position.getSymbol() + ":" + valuation.reason());
            } else {
                totalMarketValue = totalMarketValue.add(valuation.marketValue());
                totalUnrealized = totalUnrealized.add(valuation.unrealizedPnl());
            }
        }
        valuations.sort(Comparator.comparing(PositionValuation::symbol));

        totalCost = Money.money(totalCost);
        totalMarketValue = Money.money(totalMarketValue);
        totalUnrealized = Money.money(totalUnrealized);

        RiskMetrics risk = calculateRisk(account, valuations, confidence);

        if (anyImpossible) {
            String reason = "持仓缺少最新有效行情，无法计算总市值/总权益："
                    + String.join(";", impossibleReasons);
            return new PortfolioValuation(account.getAccountId(),
                    account.getCash(), account.getRealizedPnl(), totalCost,
                    null, null, null,
                    ValuationStatus.IMPOSSIBLE, reason, valuations, risk);
        }

        BigDecimal totalEquity = Money.money(account.getCash().add(totalMarketValue));
        return new PortfolioValuation(account.getAccountId(),
                account.getCash(), account.getRealizedPnl(), totalCost,
                totalMarketValue, totalUnrealized, totalEquity,
                ValuationStatus.OK, null, valuations, risk);
    }

    private PositionValuation valuePosition(Position position) {
        try {
            Quote quote = marketData.freshQuote(position.getSymbol());
            if (quote == null) {
                return PositionValuation.impossible(position.getSymbol(),
                        position.getQuantity(), position.getCostBasis(),
                        ErrorCode.ORDER_PRICE_UNAVAILABLE.name());
            }
            BigDecimal price = Money.money(quote.lastPrice());
            BigDecimal marketValue = Money.money(price.multiply(position.getQuantity()));
            BigDecimal unrealized = Money.money(
                    marketValue.subtract(position.getCostBasis()));
            return new PositionValuation(position.getSymbol(),
                    position.getQuantity(), position.getCostBasis(),
                    price, marketValue, unrealized,
                    ValuationStatus.OK, null);
        } catch (MarketDataException e) {
            return PositionValuation.impossible(position.getSymbol(),
                    position.getQuantity(), position.getCostBasis(),
                    mapMarketFailure(e));
        }
    }

    private RiskMetrics calculateRisk(Account account, List<PositionValuation> valuations,
                                      RiskConfidence confidence) {
        List<PositionValuation> held = valuations.stream()
                .filter(v -> v.quantity().signum() > 0)
                .toList();
        if (held.isEmpty()) {
            return RiskMetrics.ok(confidence.confidence(), 0,
                    BigDecimal.ZERO.setScale(Money.MONEY_SCALE));
        }

        try {
            List<List<BigDecimal>> histories = new ArrayList<>();
            int commonLength = Integer.MAX_VALUE;
            for (PositionValuation v : held) {
                List<BigDecimal> history = marketData.getHistory(v.symbol(), 10_000);
                if (history.isEmpty()) {
                    return RiskMetrics.impossible(confidence.confidence(), 0,
                            ErrorCode.RISK_PRICE_UNAVAILABLE.name() + ":" + v.symbol());
                }
                histories.add(history);
                commonLength = Math.min(commonLength, history.size());
            }

            int returnSamples = commonLength - 1;
            if (returnSamples < confidence.minHistorySamples()) {
                return RiskMetrics.impossible(confidence.confidence(), returnSamples,
                        ErrorCode.RISK_HISTORY_INSUFFICIENT.name()
                                + ": 需要 " + confidence.minHistorySamples()
                                + " 个收益样本，实际 " + returnSamples);
            }

            int returnCount = returnSamples;
            List<BigDecimal> portfolioPnls = new ArrayList<>(returnCount);
            for (int t = 1; t < commonLength; t++) {
                BigDecimal pnl = BigDecimal.ZERO.setScale(Money.MONEY_SCALE,
                        RoundingMode.HALF_UP);
                for (int h = 0; h < held.size(); h++) {
                    List<BigDecimal> history = histories.get(h);
                    int size = history.size();
                    BigDecimal prev = history.get(size - commonLength + t - 1);
                    BigDecimal current = history.get(size - commonLength + t);
                    BigDecimal ratio = current.divide(prev, 12, RoundingMode.HALF_UP);
                    BigDecimal weightValue = held.get(h).marketValue() == null
                            ? BigDecimal.ZERO
                            : held.get(h).marketValue();
                    pnl = pnl.add(weightValue.multiply(
                            ratio.subtract(BigDecimal.ONE)));
                }
                portfolioPnls.add(Money.money(pnl));
            }
            portfolioPnls.sort(Comparator.naturalOrder());

            BigDecimal alpha = BigDecimal.ONE.subtract(confidence.confidence());
            int index = alpha.multiply(BigDecimal.valueOf(portfolioPnls.size()))
                    .setScale(0, RoundingMode.DOWN)
                    .intValueExact();
            index = Math.min(Math.max(index, 0), portfolioPnls.size() - 1);
            BigDecimal quantilePnl = portfolioPnls.get(index);
            BigDecimal var = quantilePnl.signum() < 0
                    ? quantilePnl.negate()
                    : BigDecimal.ZERO.setScale(Money.MONEY_SCALE);
            return RiskMetrics.ok(confidence.confidence(), commonLength, Money.money(var));
        } catch (MarketDataException e) {
            return RiskMetrics.impossible(confidence.confidence(), 0, mapMarketFailure(e));
        }
    }

    private void validateConfidence(RiskConfidence confidence) {
        if (confidence == null
                || confidence.confidence().compareTo(BigDecimal.ZERO) <= 0
                || confidence.confidence().compareTo(BigDecimal.ONE) >= 0
                || confidence.minHistorySamples() < 2) {
            throw new ServiceException(ErrorCode.RISK_INVALID_CONFIDENCE,
                    confidence == null ? "null" : confidence.confidence().toPlainString());
        }
    }

    private String mapMarketFailure(MarketDataException e) {
        return switch (e.getKind()) {
            case TIMEOUT -> ErrorCode.MARKET_DATA_TIMEOUT.name();
            case DIRTY -> ErrorCode.MARKET_DATA_DIRTY.name();
            case UNAVAILABLE -> ErrorCode.MARKET_DATA_UNAVAILABLE.name();
        };
    }
}
