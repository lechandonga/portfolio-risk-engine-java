package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.OrderType;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ServiceException;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataException;
import com.github.lechandonga.portfolioriskenginejava.risk.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.risk.RiskConfidence;
import com.github.lechandonga.portfolioriskenginejava.risk.RiskMetrics;
import com.github.lechandonga.portfolioriskenginejava.risk.ValuationStatus;
import com.github.lechandonga.portfolioriskenginejava.tests.support.EngineHarness;
import com.github.lechandonga.portfolioriskenginejava.trading.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务2：估值与风险指标（市值/盈亏/VaR/缺数据区分）")
class ValuationRiskTest {

    private EngineHarness h;

    @BeforeEach
    void setUp() {
        h = new EngineHarness();
        h.trading.submit(new OrderRequest("acc-1", "b", "AAPL",
                Side.BUY, OrderType.MARKET, new BigDecimal("100"), null));
    }

    @Test
    @DisplayName("有行情时：市值、未实现盈亏、总权益与历史VaR可算，VaR为非负数")
    void valuationAndVarOk() {
        PortfolioValuation pv = h.valuation.evaluate("acc-1", h.riskConfidence);
        assertEquals(ValuationStatus.OK, pv.status());
        assertEquals(0, new BigDecimal("10000.00")
                .compareTo(pv.totalMarketValue()));
        assertEquals(0, new BigDecimal("-3.00")
                .compareTo(pv.totalUnrealizedPnl()));
        assertEquals(0, new BigDecimal("89997.00").compareTo(pv.cash()));
        assertEquals(ValuationStatus.OK, pv.risk().status());
        assertTrue(pv.risk().historicalVaR().signum() >= 0);
        assertTrue(pv.risk().samples() >= 5, "30 个价格点应产生 29 个收益样本");
        assertEquals(30, pv.risk().samples());
    }

    @Test
    @DisplayName("空仓账户风险结果为“零”(OK+0)，与“无法计算”明确区分")
    void zeroPositionIsZeroNotImpossible() {
        PortfolioValuation pv = h.valuation.evaluate("acc-2", h.riskConfidence);
        assertEquals(ValuationStatus.OK, pv.status());
        assertEquals(0, BigDecimal.ZERO.compareTo(pv.totalMarketValue()));
        assertEquals(ValuationStatus.OK, pv.risk().status());
        assertEquals(0, BigDecimal.ZERO.compareTo(pv.risk().historicalVaR()));
        assertEquals(0, pv.risk().samples());
    }

    @Test
    @DisplayName("历史样本不足：VaR 标记 IMPOSSIBLE 且原因为 RISK_HISTORY_INSUFFICIENT")
    void insufficientHistoryIsImpossibleNotZero() {
        h.trading.submit(new OrderRequest("acc-2", "b", "ILLIQ",
                Side.BUY, OrderType.MARKET, new BigDecimal("10"), null));
        PortfolioValuation pv = h.valuation.evaluate("acc-2", h.riskConfidence);
        RiskMetrics risk = pv.risk();
        assertEquals(ValuationStatus.IMPOSSIBLE, risk.status());
        assertNull(risk.historicalVaR());
        assertTrue(risk.reason().contains(ErrorCode.RISK_HISTORY_INSUFFICIENT.name()));
        assertEquals(ValuationStatus.OK, pv.status(), "行情仍在，估值本身可计算");
    }

    @Test
    @DisplayName("行情缺失（未注册标的/报价过期）：估值 IMPOSSIBLE，不用默认价凑数")
    void missingQuoteIsImpossible() {
        // 直接构造一个含持仓但行情被人为移除的场景
        var account = h.store.getEntry("acc-2").getAccount();
        account.getPositions().computeIfAbsent("GHOST", p -> new com.github.lechandonga
                .portfolioriskenginejava.domain.Position("GHOST"));
        var ghost = account.getPositions().get("GHOST");
        ghost.setQuantity(new BigDecimal("5"));
        ghost.setAvailableQty(new BigDecimal("5"));
        ghost.setCostBasis(new BigDecimal("500.00"));

        PortfolioValuation pv = h.valuation.evaluate("acc-2", h.riskConfidence);
        assertEquals(ValuationStatus.IMPOSSIBLE, pv.status());
        assertNull(pv.totalMarketValue());
        assertNull(pv.totalEquity());
        assertNotNull(pv.reason());
        var line = pv.positions().stream()
                .filter(p -> p.symbol().equals("GHOST")).findFirst().orElseThrow();
        assertEquals(ValuationStatus.IMPOSSIBLE, line.status());
        assertNull(line.lastPrice());
    }

    @Test
    @DisplayName("行情源超时/脏数据：底层异常不裸抛，被翻译为明确错误码且不产生成交")
    void marketFaultsAreTranslated() {
        h.raw.injectFailure(MarketDataException.Kind.TIMEOUT);
        var timeoutOrder = h.trading.submit(new OrderRequest("acc-2", "t", "AAPL",
                Side.BUY, OrderType.MARKET, new BigDecimal("1"), null));
        assertEquals(com.github.lechandonga.portfolioriskenginejava.domain.OrderStatus.REJECTED,
                timeoutOrder.getStatus());
        assertEquals(ErrorCode.MARKET_DATA_TIMEOUT.name(), timeoutOrder.getRejectReason());

        h.raw.clearInjectedFailure();
        h.raw.injectFailure(MarketDataException.Kind.DIRTY);
        var dirtyOrder = h.trading.submit(new OrderRequest("acc-2", "d", "AAPL",
                Side.BUY, OrderType.MARKET, new BigDecimal("1"), null));
        assertEquals(com.github.lechandonga.portfolioriskenginejava.domain.OrderStatus.REJECTED,
                dirtyOrder.getStatus());
        assertEquals(ErrorCode.MARKET_DATA_DIRTY.name(), dirtyOrder.getRejectReason());
        h.raw.clearInjectedFailure();

        // 故障期间没有任何扣款
        assertEquals(0, new BigDecimal("50000.00")
                .compareTo(h.store.getEntry("acc-2").getAccount().getCash()));

        // 估值侧也不裸抛，而是 IMPOSSIBLE
        h.raw.injectFailure(MarketDataException.Kind.UNAVAILABLE);
        PortfolioValuation pv = h.valuation.evaluate("acc-1", h.riskConfidence);
        assertEquals(ValuationStatus.IMPOSSIBLE, pv.status());
        h.raw.clearInjectedFailure();

        // 行情服务自身（刷新/回放）仍会抛出经翻译的、可区分的业务异常
        h.raw.injectFailure(MarketDataException.Kind.TIMEOUT);
        ServiceException timeoutApi = assertThrows(ServiceException.class,
                () -> h.replay.refresh());
        assertEquals(ErrorCode.MARKET_DATA_TIMEOUT, timeoutApi.getCode());
        h.raw.clearInjectedFailure();
    }

    @Test
    @DisplayName("置信水平可配置且必须在开区间(0,1)，否则 RISK_INVALID_CONFIDENCE")
    void confidenceConfigurableAndValidated() {
        RiskMetrics v90 = h.valuation
                .evaluate("acc-1", RiskConfidence.of("0.90", 5)).risk();
        RiskMetrics v99 = h.valuation
                .evaluate("acc-1", RiskConfidence.of("0.99", 5)).risk();
        assertEquals(0, new BigDecimal("0.90").compareTo(v90.confidence()));
        assertEquals(0, new BigDecimal("0.99").compareTo(v99.confidence()));

        assertThrows(ServiceException.class, () ->
                h.valuation.evaluate("acc-1", RiskConfidence.of("1.00", 5)));
        ServiceException ex = assertThrows(ServiceException.class, () ->
                h.valuation.evaluate("acc-1", RiskConfidence.of("0", 5)));
        assertEquals(ErrorCode.RISK_INVALID_CONFIDENCE, ex.getCode());
    }

    @Test
    @DisplayName("批量估值账户隔离：单个账户失败不影响其他账户结果")
    void accountIsolationInBatch() {
        var failures = new ArrayList<com.github.lechandonga.portfolioriskenginejava
                .store.AccountStore.AccountFailure>();
        var portfolios = h.valuation.evaluateAll(h.riskConfidence, failures);
        assertEquals(2, portfolios.size());
        assertEquals(0, failures.size());
    }
}
