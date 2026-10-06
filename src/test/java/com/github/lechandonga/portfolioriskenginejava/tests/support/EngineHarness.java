package com.github.lechandonga.portfolioriskenginejava.tests.support;

import com.github.lechandonga.portfolioriskenginejava.corporate.CorporateActionService;
import com.github.lechandonga.portfolioriskenginejava.market.MarketReplayService;
import com.github.lechandonga.portfolioriskenginejava.market.SimClock;
import com.github.lechandonga.portfolioriskenginejava.market.SimulatedMarketDataProvider;
import com.github.lechandonga.portfolioriskenginejava.market.ValidatingMarketDataProvider;
import com.github.lechandonga.portfolioriskenginejava.risk.RiskConfidence;
import com.github.lechandonga.portfolioriskenginejava.risk.ValuationService;
import com.github.lechandonga.portfolioriskenginejava.store.AccountStore;
import com.github.lechandonga.portfolioriskenginejava.trading.FeePolicy;
import com.github.lechandonga.portfolioriskenginejava.trading.TradingEngine;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/** 测试装配：手工构建全套本地服务，避免单测依赖 Spring 上下文以便并发压测。 */
public class EngineHarness {

    public final SimClock clock = new SimClock();
    public final SimulatedMarketDataProvider raw;
    public final ValidatingMarketDataProvider marketData;
    public final AccountStore store = new AccountStore();
    public final FeePolicy feePolicy =
            new FeePolicy(new BigDecimal("0.0003"), new BigDecimal("1.00"));
    public final TradingEngine trading;
    public final ValuationService valuation;
    public final CorporateActionService corporate;
    public final MarketReplayService replay;
    public final RiskConfidence riskConfidence = new RiskConfidence(
            new BigDecimal("0.95"), 5);

    public EngineHarness() {
        raw = new SimulatedMarketDataProvider(clock);
        marketData = new ValidatingMarketDataProvider(raw, clock, Duration.ofHours(1));
        raw.registerSymbol("AAPL", new BigDecimal("100"),
                seeded(new BigDecimal("100"), 30, 1));
        raw.registerSymbol("MSFT", new BigDecimal("200"),
                seeded(new BigDecimal("200"), 30, 7));
        raw.registerSymbol("ILLIQ", new BigDecimal("50"),
                List.of(new BigDecimal("50")));
        trading = new TradingEngine(store, marketData, feePolicy, clock);
        valuation = new ValuationService(store, marketData);
        corporate = new CorporateActionService(store);
        replay = new MarketReplayService(raw);
        store.createAccount("acc-1", new BigDecimal("100000.00"));
        store.createAccount("acc-2", new BigDecimal("50000.00"));
    }

    private static List<BigDecimal> seeded(BigDecimal base, int n, int seedPhase) {
        java.util.List<BigDecimal> list = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            double wave = Math.sin(i / 3.0d + seedPhase) * 0.02d;
            list.add(base.multiply(BigDecimal.valueOf(1 + wave)));
        }
        // 最后一点（最新价）精确落在基准价，使成交金额断言可写成确定值
        list.set(n - 1, base);
        return list;
    }
}
