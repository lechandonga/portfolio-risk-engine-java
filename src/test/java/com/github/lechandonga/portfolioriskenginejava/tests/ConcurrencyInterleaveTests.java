package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.LedgerEntry;
import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import com.github.lechandonga.portfolioriskenginejava.domain.Position;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.market.LocalMarketDataSource;
import com.github.lechandonga.portfolioriskenginejava.market.Quote;
import com.github.lechandonga.portfolioriskenginejava.service.AccountRepository;
import com.github.lechandonga.portfolioriskenginejava.service.InstrumentRepository;
import com.github.lechandonga.portfolioriskenginejava.service.LedgerRepository;
import com.github.lechandonga.portfolioriskenginejava.service.PortfolioBatchService;
import com.github.lechandonga.portfolioriskenginejava.service.PositionRepository;
import com.github.lechandonga.portfolioriskenginejava.service.TradingService;
import com.github.lechandonga.portfolioriskenginejava.tests.support.BusinessTestBase;
import com.github.lechandonga.portfolioriskenginejava.tests.support.ControllableClockConfig;
import com.github.lechandonga.portfolioriskenginejava.tests.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ControllableClockConfig.class)
class ConcurrencyInterleaveTests extends BusinessTestBase {
    @Autowired TradingService trading;
    @Autowired AccountRepository accounts;
    @Autowired InstrumentRepository instruments;
    @Autowired LedgerRepository ledger;
    @Autowired PositionRepository positions;
    @Autowired LocalMarketDataSource market;
    @Autowired PortfolioBatchService batch;
    @Autowired AtomicLong clockValue;

    private String acct;

    @Override
    protected String businessModule() {
        return "并发交错-守恒、幂等与账户隔离";
    }

    @BeforeEach
    void setup() {
        acct = "cc-" + UUID.randomUUID();
        market.clearFault();
        market.clearFault();
        TestFixtures.account(accounts, ledger, clockValue::get, acct, "5000000");
        TestFixtures.instrument(instruments, "AAA", 100, true);
        TestFixtures.freshQuote(market, clockValue::get, "AAA", "10.00");
    }

    @Test
    @DisplayName("同一 clientRequestId 并发重试: 只创建一张订单, 只冻结一次")
    void concurrentDuplicateSubmitCreatesOnce() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Order> results = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                results.add(trading.submit(acct,
                        new OrderRequest("race-1", Side.BUY, "AAA", 100, null)));
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(1, results.stream().map(Order::getOrderId).distinct().count());
        // 100 shares * 10.00 + min fee 5.00 = 1005 reserved exactly once.
        assertEquals(0, new BigDecimal("1005.00").compareTo(accounts.get(acct).getReservedCash()));
    }

    @Test
    @DisplayName("下单/成交/撤单/行情刷新交错: 台账守恒, 终态无残留冻结")
    void interleavedMutationsConserveLedger() throws Exception {
        var base = trading.submit(acct, new OrderRequest("base", Side.BUY, "AAA", 50_000, null));
        trading.fill(acct, base.getOrderId());

        int workers = 8;
        int perWorker = 40;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        for (int w = 0; w < workers; w++) {
            final int workerId = w;
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perWorker; i++) {
                    try {
                        market.publish(new Quote("AAA", new BigDecimal("10.00"), clockValue.get()));
                        Side side = (workerId + i) % 2 == 0 ? Side.BUY : Side.SELL;
                        Order order = trading.submit(acct,
                                new OrderRequest("w" + workerId + "-" + i, side, "AAA", 100, null));
                        if (i % 3 == 0) {
                            trading.cancel(acct, order.getOrderId());
                        } else {
                            trading.fill(acct, order.getOrderId());
                        }
                    } catch (RuntimeException ignored) {
                        // Expected race-loser/valid-reject paths; ledger assertions below catch bugs.
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // FEE_DETAIL is an informational breakdown already included in BUY/SELL cash lines.
        BigDecimal ledgerSum = ledger.findByAccount(acct).stream()
                .filter(e -> !e.type().equals("FEE_DETAIL"))
                .map(LedgerEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var byType2 = ledger.findByAccount(acct).stream()
                .collect(java.util.stream.Collectors.groupingBy(LedgerEntry::type,
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO, LedgerEntry::amount,
                                BigDecimal::add)));
        log.info("台账汇总: {}", byType2);
        assertEquals(0, ledgerSum.compareTo(accounts.get(acct).getCash()),
                "现金余额必须等于台账流水代数和");
        assertEquals(0, accounts.get(acct).getReservedCash().compareTo(BigDecimal.ZERO),
                "所有订单终态后不能残留冻结资金");
        Position position = positions.get(acct, "AAA");
        assertEquals(0, position.getReservedQuantity(), "不能残留冻结持仓");

        BigDecimal fees = ledger.findByAccount(acct).stream()
                .filter(e -> e.type().equals("FEE_DETAIL")).map(LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).negate();
        var byType = ledger.findByAccount(acct).stream()
                .collect(java.util.stream.Collectors.groupingBy(LedgerEntry::type,
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO, LedgerEntry::amount,
                                BigDecimal::add)));
        log.info("台账汇总: BUY={} SELL={} FEE={} DEPOSIT={}", byType.get("BUY"),
                byType.get("SELL"), byType.get("FEE_DETAIL"), byType.get("DEPOSIT"));
        // At a flat mark price net worth declines only by fees:
        // cash + MV + fees == initial deposit (realised losses are already inside cash).
        BigDecimal netWorth = accounts.get(acct).getCash()
                .add(BigDecimal.valueOf(position.getQuantity()).multiply(new BigDecimal("10.00")))
                .add(fees);
        assertEquals(0, netWorth.setScale(2, RoundingMode.HALF_UP)
                        .compareTo(new BigDecimal("5000000.00")),
                "恒定价格下 现金+市值-已实现盈亏+费用 必须等于初始资金: cash="
                        + accounts.get(acct).getCash() + ", realized=" + accounts.get(acct).getRealizedPnl()
                        + ", fees=" + fees + ", netWorth=" + netWorth);
        log.info("总账守恒校验: 现金+市值+费用={}, 累计费用={} (应等于初始资金 5000000)",
                netWorth, fees);
    }

    @Test
    @DisplayName("一个账户行情故障: 批量估值/风险中其他账户仍正常返回")
    void oneAccountFailureIsIsolatedInBatch() {
        String good = "cc-good-" + UUID.randomUUID();
        String bad = "cc-bad-" + UUID.randomUUID();
        market.clearFault();
        market.clearFault();
        TestFixtures.account(accounts, ledger, clockValue::get, good, "100000");
        TestFixtures.account(accounts, ledger, clockValue::get, bad, "100000");
        var g = trading.submit(good, new OrderRequest("g", Side.BUY, "AAA", 100, null));
        trading.fill(good, g.getOrderId());
        TestFixtures.instrument(instruments, "BRK", 100, true);
        TestFixtures.freshQuote(market, clockValue::get, "BRK", "7.00");
        var b = trading.submit(bad, new OrderRequest("b", Side.BUY, "BRK", 100, null));
        trading.fill(bad, b.getOrderId());
        market.setStickyFault(LocalMarketDataSource.Fault.DIRTY);

        var valuations = batch.valueAll();
        assertNotNull(valuations.get(good).result());
        assertNull(valuations.get(good).errorCode());
        assertNotNull(valuations.get(bad).result(), "行情故障按 UNAVAILABLE 行返回, 不应抛穿批量接口");

        var risks = batch.riskAll(null);
        assertNotNull(risks.get(good).result());
        assertNotNull(risks.get(bad).result());
        assertTrue(risks.get(bad).result().notComputableSymbols().contains("BRK"));
    }
}
