package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.PositionValuation;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.ValuationStatus;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.market.LocalMarketDataSource;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataService;
import com.github.lechandonga.portfolioriskenginejava.service.AccountRepository;
import com.github.lechandonga.portfolioriskenginejava.service.InstrumentRepository;
import com.github.lechandonga.portfolioriskenginejava.service.LedgerRepository;
import com.github.lechandonga.portfolioriskenginejava.service.PositionRepository;
import com.github.lechandonga.portfolioriskenginejava.service.TradingService;
import com.github.lechandonga.portfolioriskenginejava.service.ValuationService;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ControllableClockConfig.class)
class ValuationMarketDataTests extends BusinessTestBase {
    @Autowired TradingService trading;
    @Autowired ValuationService valuation;
    @Autowired MarketDataService marketData;
    @Autowired AccountRepository accounts;
    @Autowired InstrumentRepository instruments;
    @Autowired LedgerRepository ledger;
    @Autowired PositionRepository positions;
    @Autowired LocalMarketDataSource market;
    @Autowired AtomicLong clockValue;

    private String acct;

    @Override
    protected String businessModule() {
        return "估值-盈亏与行情缺失语义";
    }

    @BeforeEach
    void setup() {
        acct = "val-" + UUID.randomUUID();
        market.clearFault();
        TestFixtures.account(accounts, ledger, clockValue::get, acct, "100000");
        TestFixtures.instrument(instruments, "AAA", 100, true);
        TestFixtures.freshQuote(market, clockValue::get, "AAA", "10.00");
        var order = trading.submit(acct, new OrderRequest("v-buy", Side.BUY, "AAA", 1000, null));
        trading.fill(acct, order.getOrderId());
    }

    @Test
    @DisplayName("最新行情估值: 市值、未实现盈亏、总权益正确")
    void marketValueAndUnrealizedPnl() {
        TestFixtures.freshQuote(market, clockValue::get, "AAA", "11.00");
        PortfolioValuation valuationReport = valuation.value(acct);
        PositionValuation line = valuationReport.positions().get(0);
        assertEquals(ValuationStatus.OK, line.status());
        assertEquals(0, new BigDecimal("11000.00").compareTo(line.marketValue()));
        assertTrue(line.unrealizedPnl().doubleValue() > 0);
        assertEquals(0, valuationReport.totalEquity().compareTo(
                accounts.get(acct).getCash().add(new BigDecimal("11000.00"))));
    }

    @Test
    @DisplayName("卖出后已实现盈亏累计, 剩余持仓成本按平均成本结转")
    void realizedPnlOnSell() {
        var sell = trading.submit(acct, new OrderRequest("v-sell", Side.SELL, "AAA", 400, null));
        trading.fill(acct, sell.getOrderId());
        BigDecimal realized = accounts.get(acct).getRealizedPnl();
        // Mark equals cost; the only PnL is sell fees (commission + duty), hence a loss.
        assertTrue(realized.doubleValue() < 0, "同价卖出扣除费用后已实现盈亏应为负, 实际=" + realized);
        PortfolioValuation report = valuation.value(acct);
        PositionValuation remaining = report.positions().stream()
                .filter(p -> p.symbol().equals("AAA")).findFirst().orElseThrow();
        assertEquals(600, remaining.quantity());
    }

    @Test
    @DisplayName("行情缺失: 标记 UNAVAILABLE 并剔除出总额, 而不是按默认价计算")
    void missingQuoteIsUnavailableNotZero() {
        market.setFault(LocalMarketDataSource.Fault.MISSING);
        PortfolioValuation report = valuation.value(acct);
        PositionValuation line = report.positions().get(0);
        assertEquals(ValuationStatus.MARKET_DATA_UNAVAILABLE, line.status());
        assertNull(line.marketValue(), "不能用默认价格填充市值");
        assertTrue(report.unavailableSymbols().contains("AAA"));
        assertEquals(0, report.totalMarketValue().compareTo(BigDecimal.ZERO),
                "无法估值的持仓不得计入总额");
    }

    @Test
    @DisplayName("过期行情: 报 MARKET_DATA_STALE, 不静默沿用旧价")
    void staleQuoteRejected() {
        market.clearFault();
        TestFixtures.oldQuote(market, clockValue::get, "AAA", "999.00", 120_000);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> marketData.requireFreshQuote("AAA"));
        assertEquals(ErrorCode.MARKET_DATA_STALE, ex.getCode());
    }

    @Test
    @DisplayName("来源超时: 底层等待被转换为 MARKET_DATA_TIMEOUT, 调用方见不到底层异常")
    void timeoutNormalised() {
        market.setFault(LocalMarketDataSource.Fault.TIMEOUT);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> marketData.requireFreshQuote("AAA"));
        assertEquals(ErrorCode.MARKET_DATA_TIMEOUT, ex.getCode());
        market.clearFault();
    }

    @Test
    @DisplayName("来源脏数据(负价/损坏): 报 MARKET_DATA_DIRTY")
    void dirtyDataRejected() {
        market.setFault(LocalMarketDataSource.Fault.DIRTY);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> marketData.requireFreshQuote("AAA"));
        assertEquals(ErrorCode.MARKET_DATA_DIRTY, ex.getCode());
        assertNotNull(positions.get(acct, "AAA"));
    }
}
