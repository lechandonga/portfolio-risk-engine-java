package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.CorporateActionRequest;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.PortfolioValuation;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.market.LocalMarketDataSource;
import com.github.lechandonga.portfolioriskenginejava.service.AccountRepository;
import com.github.lechandonga.portfolioriskenginejava.service.CorporateActionRepository;
import com.github.lechandonga.portfolioriskenginejava.service.CorporateActionService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ControllableClockConfig.class)
class CorporateActionTests extends BusinessTestBase {
    @Autowired CorporateActionService corporateActions;
    @Autowired CorporateActionRepository actionRepo;
    @Autowired TradingService trading;
    @Autowired ValuationService valuation;
    @Autowired AccountRepository accounts;
    @Autowired InstrumentRepository instruments;
    @Autowired LedgerRepository ledger;
    @Autowired PositionRepository positions;
    @Autowired LocalMarketDataSource market;
    @Autowired AtomicLong clockValue;

    private String acct;

    @Override
    protected String businessModule() {
        return "公司行为-拆股与现金分红";
    }

    @BeforeEach
    void setup() {
        acct = "ca-" + UUID.randomUUID();
        market.clearFault();
        TestFixtures.account(accounts, ledger, clockValue::get, acct, "100000");
        TestFixtures.instrument(instruments, "AAA", 100, true);
        TestFixtures.freshQuote(market, clockValue::get, "AAA", "10.00");
        var order = trading.submit(acct, new OrderRequest("ca-buy", Side.BUY, "AAA", 1000, null));
        trading.fill(acct, order.getOrderId());
    }

    @Test
    @DisplayName("2:1 拆股: 股数翻倍、总成本不变、均价减半, 新价下市值自洽")
    void splitAdjustsSharesAndKeepsCostBasis() {
        var action = corporateActions.register(new CorporateActionRequest(
                "split-1", "AAA", CorporateAction.Type.SPLIT, new BigDecimal("2"), null, null));
        BigDecimal costBefore = positions.get(acct, "AAA").getCostBasis();
        corporateActions.apply(acct, action.getActionId());
        assertEquals(2000, positions.get(acct, "AAA").getQuantity());
        assertEquals(0, costBefore.compareTo(positions.get(acct, "AAA").getCostBasis()));
        // Market adjusts price 10 -> 5; market value unchanged in a clean split.
        TestFixtures.freshQuote(market, clockValue::get, "AAA", "5.00");
        PortfolioValuation report = valuation.value(acct);
        assertEquals(0, new BigDecimal("10000.00").compareTo(report.totalMarketValue()));
    }

    @Test
    @DisplayName("现金分红: 按持仓股数派现一次并入账")
    void cashDividendPaidOnce() {
        BigDecimal cashBefore = accounts.get(acct).getCash();
        var action = corporateActions.register(new CorporateActionRequest(
                "div-1", "AAA", CorporateAction.Type.CASH_DIVIDEND, null, new BigDecimal("0.50"), null));
        corporateActions.apply(acct, action.getActionId());
        assertEquals(0, cashBefore.add(new BigDecimal("500.00")).compareTo(accounts.get(acct).getCash()));
    }

    @Test
    @DisplayName("重复应用同一公司行为: 第二次被 CORPORATE_ACTION_DUPLICATE 拒绝, 不重复调整")
    void duplicateApplicationRejected() {
        var action = corporateActions.register(new CorporateActionRequest(
                "div-2", "AAA", CorporateAction.Type.CASH_DIVIDEND, null, new BigDecimal("1.00"), null));
        corporateActions.apply(acct, action.getActionId());
        BigDecimal cashAfterFirst = accounts.get(acct).getCash();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> corporateActions.apply(acct, action.getActionId()));
        assertEquals(ErrorCode.CORPORATE_ACTION_DUPLICATE, ex.getCode());
        assertEquals(cashAfterFirst, accounts.get(acct).getCash(), "重复分红不得再次派现");
    }

    @Test
    @DisplayName("公司行为针对未知标的: 注册阶段被拒绝")
    void unknownInstrumentRejectedAtRegistration() {
        BusinessException ex = assertThrows(BusinessException.class, () -> corporateActions.register(
                new CorporateActionRequest("bad-1", "ZZZ", CorporateAction.Type.SPLIT,
                        new BigDecimal("2"), null, null)));
        assertEquals(ErrorCode.CORPORATE_ACTION_UNKNOWN_INSTRUMENT, ex.getCode());
    }
}
