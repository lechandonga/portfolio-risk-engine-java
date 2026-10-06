package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.OrderStatus;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderType;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.market.MarketReplayService;
import com.github.lechandonga.portfolioriskenginejava.market.SimClock;
import com.github.lechandonga.portfolioriskenginejava.market.SimulatedMarketDataProvider;
import com.github.lechandonga.portfolioriskenginejava.market.ValidatingMarketDataProvider;
import com.github.lechandonga.portfolioriskenginejava.risk.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.risk.RiskConfidence;
import com.github.lechandonga.portfolioriskenginejava.risk.ValuationService;
import com.github.lechandonga.portfolioriskenginejava.store.AccountStore;
import com.github.lechandonga.portfolioriskenginejava.trading.FeePolicy;
import com.github.lechandonga.portfolioriskenginejava.trading.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.trading.TradingEngine;
import com.github.lechandonga.portfolioriskenginejava.tests.support.EngineHarness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务4/5：并发交错总账守恒 + 确定性行情回放可复现")
class ConcurrencyAndReplayTest {

    /**
     * 总账守恒：在固定价格（不刷新行情）下，买入、卖出、撤单、重复提交交错执行后，
     * 恒等式 现金 + 持仓成本 + 已实现盈亏 + 累计费用 = 初始现金 必须成立。
     */
    @Test
    @DisplayName("并发下单/撤单/重复提交交错：现金、持仓、费用总账守恒")
    void concurrentInterleavingConservesLedger() throws Exception {
        EngineHarness h = new EngineHarness();
        int threads = 12;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<Exception> errors = ConcurrentHashMap.newKeySet();

        for (int t = 0; t < threads; t++) {
            final int idx = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        String clientId = "t" + idx + "-" + i;
                        try {
                            var order = h.trading.submit(new OrderRequest("acc-1",
                                    clientId, "AAPL", Side.BUY, OrderType.MARKET,
                                    new BigDecimal("1"), null));
                            if (idx % 2 == 0) {
                                h.trading.submit(new OrderRequest("acc-1",
                                        clientId, "AAPL", Side.BUY, OrderType.MARKET,
                                        new BigDecimal("1"), null));
                            }
                            if (idx % 3 == 0) {
                                h.trading.submit(new OrderRequest("acc-1",
                                        clientId + "-s", "AAPL", Side.SELL,
                                        OrderType.MARKET, new BigDecimal("1"), null));
                            }
                            if (order.getStatus() == OrderStatus.NEW) {
                                h.trading.cancel("acc-1", order.getId());
                            }
                        } catch (RuntimeException ignored) {
                            // INSUFFICIENT_* 等业务拒绝是允许的结果
                        }
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(errors.isEmpty(), () -> "出现非业务异常: " + errors);

        var account = h.store.getEntry("acc-1").getAccount();
        BigDecimal fees = h.store.getEntry("acc-1").getOrders().values().stream()
                .filter(o -> o.getFee() != null)
                .map(o -> o.getFee())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal positionCost = account.getPositions().values().stream()
                .map(p -> p.getCostBasis())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ledger = account.getCash()
                .add(positionCost)
                .add(account.getRealizedPnl())
                .add(fees);
        assertEquals(0, new BigDecimal("100000.00").compareTo(ledger),
                "现金+持仓成本+已实现盈亏+累计费用必须等于初始现金");

        var position = account.getPositions().get("AAPL");
        if (position != null) {
            assertTrue(position.getAvailableQty().compareTo(position.getQuantity()) <= 0);
        }
    }

    @Test
    @DisplayName("下单与行情刷新并发交错：现金+市值=总权益 的估值恒等式始终成立")
    void tradingVsRefreshAlwaysBalanced() throws Exception {
        EngineHarness h = new EngineHarness();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> checks = new ArrayList<>();
        for (int t = 0; t < 3; t++) {
            final int idx = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 40; i++) {
                        h.trading.submit(new OrderRequest("acc-1",
                                "m" + idx + "-" + i, "AAPL",
                                i % 2 == 0 ? Side.BUY : Side.SELL, OrderType.MARKET,
                                new BigDecimal("1"), null));
                    }
                } catch (Exception ignored) {
                }
            });
        }
        for (int t = 0; t < 3; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 40; i++) {
                        h.replay.refresh();
                        PortfolioValuation pv =
                                h.valuation.evaluate("acc-1", h.riskConfidence);
                        if (pv.totalEquity() != null) {
                            synchronized (checks) {
                                checks.add(pv.cash().add(pv.totalMarketValue())
                                        .compareTo(pv.totalEquity()) == 0);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        assertTrue(checks.stream().allMatch(Boolean::booleanValue));
        assertTrue(checks.size() > 10);
    }

    @Test
    @DisplayName("相同输入+相同行情回放：两次独立运行的估值与VaR完全一致")
    void deterministicReplayIsReproducible() {
        PortfolioValuation run1 = scenario();
        PortfolioValuation run2 = scenario();
        assertEquals(run1.totalMarketValue(), run2.totalMarketValue());
        assertEquals(run1.totalEquity(), run2.totalEquity());
        assertEquals(run1.totalUnrealizedPnl(), run2.totalUnrealizedPnl());
        assertEquals(run1.risk().historicalVaR(), run2.risk().historicalVaR());
        assertEquals(run1.risk().samples(), run2.risk().samples());
    }

    private PortfolioValuation scenario() {
        SimClock clock = new SimClock();
        SimulatedMarketDataProvider raw = new SimulatedMarketDataProvider(clock);
        List<BigDecimal> seed = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            seed.add(BigDecimal.valueOf(100 + i));
        }
        raw.registerSymbol("AAPL", BigDecimal.valueOf(100), seed);
        ValidatingMarketDataProvider marketData =
                new ValidatingMarketDataProvider(raw, clock, Duration.ofHours(24));
        AccountStore accountStore = new AccountStore();
        accountStore.createAccount("a", new BigDecimal("100000"));
        FeePolicy fee = new FeePolicy(new BigDecimal("0.0003"), BigDecimal.ONE);
        TradingEngine engine = new TradingEngine(
                accountStore, marketData, fee, clock);
        ValuationService valuation = new ValuationService(accountStore, marketData);
        MarketReplayService replay = new MarketReplayService(raw);

        Instant t = SimClock.EPOCH;
        replay.replay(List.of(
                new MarketReplayService.ReplayTick("AAPL", new BigDecimal("101"),
                        t.plusSeconds(60)),
                new MarketReplayService.ReplayTick("AAPL", new BigDecimal("103"),
                        t.plusSeconds(120)),
                new MarketReplayService.ReplayTick("AAPL", new BigDecimal("104"),
                        t.plusSeconds(180))));
        engine.submit(new OrderRequest("a", "buy-1", "AAPL", Side.BUY,
                OrderType.MARKET, new BigDecimal("10"), null));
        replay.replay(List.of(
                new MarketReplayService.ReplayTick("AAPL", new BigDecimal("105"),
                        t.plusSeconds(240))));
        return valuation.evaluate("a", new RiskConfidence(
                new BigDecimal("0.95"), 5));
    }
}
