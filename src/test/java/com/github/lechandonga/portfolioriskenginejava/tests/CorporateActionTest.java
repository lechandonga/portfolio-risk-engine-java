package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;
import com.github.lechandonga.portfolioriskenginejava.corporate.CorporateActionResult;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderType;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ServiceException;
import com.github.lechandonga.portfolioriskenginejava.risk.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.risk.ValuationStatus;
import com.github.lechandonga.portfolioriskenginejava.tests.support.EngineHarness;
import com.github.lechandonga.portfolioriskenginejava.trading.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务3：拆股与现金分红（调整/幂等/自洽）")
class CorporateActionTest {

    private EngineHarness h;

    @BeforeEach
    void setUp() {
        h = new EngineHarness();
        h.trading.submit(new OrderRequest("acc-1", "b", "AAPL",
                Side.BUY, OrderType.MARKET, new BigDecimal("100"), null));
    }

    @Test
    @DisplayName("1拆2：数量翻倍、单位成本减半、总成本与现金不变")
    void splitAdjustsQtyKeepsTotalCost() {
        var accountBefore = h.store.getEntry("acc-1").getAccount();
        BigDecimal cashBefore = accountBefore.getCash();
        BigDecimal costBefore = accountBefore.getPositions().get("AAPL").getCostBasis();

        CorporateActionResult r = h.corporate.apply("acc-1", new CorporateAction(
                "ca-split-1", "AAPL", CorporateAction.Type.SPLIT,
                new BigDecimal("2"), null));

        assertEquals(false, r.duplicated());
        var position = accountBefore.getPositions().get("AAPL");
        assertEquals(0, new BigDecimal("200").compareTo(position.getQuantity()));
        assertEquals(0, new BigDecimal("200").compareTo(position.getAvailableQty()));
        assertEquals(0, costBefore.compareTo(position.getCostBasis()));
        assertEquals(0, cashBefore.compareTo(accountBefore.getCash()));
    }

    @Test
    @DisplayName("拆股重复应用不重复调整，返回 duplicated=true")
    void splitIsIdempotent() {
        CorporateAction action = new CorporateAction("ca-split-x", "AAPL",
                CorporateAction.Type.SPLIT, new BigDecimal("3"), null);
        h.corporate.apply("acc-1", action);
        CorporateActionResult again = h.corporate.apply("acc-1", action);
        assertTrue(again.duplicated());
        assertEquals(0, new BigDecimal("300").compareTo(
                h.store.getEntry("acc-1").getAccount()
                        .getPositions().get("AAPL").getQuantity()));
    }

    @Test
    @DisplayName("现金分红：现金与已实现盈亏等额增加，数量成本不变；重复分红不重复派息")
    void dividendCreditsCashOnce() {
        CorporateActionResult r = h.corporate.apply("acc-1", new CorporateAction(
                "ca-div-1", "AAPL", CorporateAction.Type.DIVIDEND,
                null, new BigDecimal("0.50")));
        assertEquals(0, new BigDecimal("50.00").compareTo(r.cashPaid()));
        var account = h.store.getEntry("acc-1").getAccount();
        assertEquals(0, new BigDecimal("90047.00").compareTo(account.getCash()));
        assertEquals(0, new BigDecimal("50.00").compareTo(account.getRealizedPnl()));
        assertEquals(0, new BigDecimal("100").compareTo(
                account.getPositions().get("AAPL").getQuantity()));

        CorporateActionResult again = h.corporate.apply("acc-1", new CorporateAction(
                "ca-div-1", "AAPL", CorporateAction.Type.DIVIDEND,
                null, new BigDecimal("0.50")));
        assertTrue(again.duplicated());
        assertEquals(0, new BigDecimal("90047.00").compareTo(account.getCash()));
        assertEquals(0, new BigDecimal("50.00").compareTo(account.getRealizedPnl()));
    }

    @Test
    @DisplayName("存在未成交卖单时拒绝公司行为，防止预留失真")
    void rejectWithOpenSell() {
        h.trading.submit(new OrderRequest("acc-1", "s", "AAPL",
                Side.SELL, OrderType.LIMIT, new BigDecimal("10"),
                new BigDecimal("300.00")));
        ServiceException ex = assertThrowsCa(new CorporateAction(
                "ca-blocked", "AAPL", CorporateAction.Type.SPLIT,
                new BigDecimal("2"), null));
        assertEquals(ErrorCode.CORPORATE_ACTION_OPEN_ORDERS, ex.getCode());
    }

    @Test
    @DisplayName("调整后估值自洽：分红后总权益 = 原总权益 + 分红；拆股后市值仍按新数量×价格")
    void valuationConsistentAfterActions() {
        PortfolioValuation before = h.valuation.evaluate("acc-1", h.riskConfidence);
        h.corporate.apply("acc-1", new CorporateAction("ca-d", "AAPL",
                CorporateAction.Type.DIVIDEND, null, new BigDecimal("1.00")));
        PortfolioValuation after = h.valuation.evaluate("acc-1", h.riskConfidence);
        assertEquals(0, before.totalEquity().add(new BigDecimal("100.00"))
                .compareTo(after.totalEquity()));

        h.corporate.apply("acc-1", new CorporateAction("ca-s", "AAPL",
                CorporateAction.Type.SPLIT, new BigDecimal("2"), null));
        PortfolioValuation split = h.valuation.evaluate("acc-1", h.riskConfidence);
        var position = split.positions().stream()
                .filter(p -> p.symbol().equals("AAPL")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("200").compareTo(position.quantity()));
        // 最新价未变，市值翻倍，但未实现盈亏 = 市值(20000) - 成本(10003)
        assertEquals(0, new BigDecimal("20000.00").compareTo(position.marketValue()));
        assertEquals(ValuationStatus.OK, split.status());
    }

    private ServiceException assertThrowsCa(CorporateAction action) {
        ServiceException caught = null;
        try {
            h.corporate.apply("acc-1", action);
        } catch (ServiceException e) {
            caught = e;
        }
        if (caught == null) {
            throw new AssertionError("expected ServiceException");
        }
        return caught;
    }
}
