package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.RiskReport;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.RiskStatus;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.market.LocalMarketDataSource;
import com.github.lechandonga.portfolioriskenginejava.service.AccountRepository;
import com.github.lechandonga.portfolioriskenginejava.service.InstrumentRepository;
import com.github.lechandonga.portfolioriskenginejava.service.LedgerRepository;
import com.github.lechandonga.portfolioriskenginejava.service.RiskService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ControllableClockConfig.class)
class RiskModelTests extends BusinessTestBase {
    @Autowired RiskService risk;
    @Autowired TradingService trading;
    @Autowired AccountRepository accounts;
    @Autowired InstrumentRepository instruments;
    @Autowired LedgerRepository ledger;
    @Autowired LocalMarketDataSource market;
    @Autowired AtomicLong clockValue;

    private String acct;

    @Override
    protected String businessModule() {
        return "风险指标-VaR/ES 与样本不足";
    }

    @BeforeEach
    void setup() {
        acct = "risk-" + UUID.randomUUID();
        market.clearFault();
        market.clearFault();
        TestFixtures.account(accounts, ledger, clockValue::get, acct, "1000000");
        TestFixtures.instrument(instruments, "AAA", 100, true);
        TestFixtures.freshQuote(market, clockValue::get, "AAA", "10.00");
        var order = trading.submit(acct, new OrderRequest("r-buy", Side.BUY, "AAA", 1000, null));
        trading.fill(acct, order.getOrderId());
    }

    @Test
    @DisplayName("充足历史: 按置信水平产出非负 VaR 与 ES, ES >= VaR")
    void varAndExpectedShortfallComputed() {
        List<BigDecimal> history = new ArrayList<>();
        double price = 10.0;
        var rnd = new java.util.Random(123);
        for (int i = 0; i < 200; i++) {
            price *= 1 + rnd.nextGaussian() * 0.02;
            history.add(BigDecimal.valueOf(price));
        }
        market.publishHistory("AAA", history);
        TestFixtures.freshQuote(market, clockValue::get, "AAA", BigDecimal.valueOf(price).toString());

        RiskReport report95 = risk.report(acct, new BigDecimal("0.95"));
        RiskReport report99 = risk.report(acct, new BigDecimal("0.99"));
        assertEquals(RiskStatus.OK, report95.positions().get(0).status());
        assertTrue(report95.portfolioVar().doubleValue() >= 0,
                "VaR should be non-negative: " + report95.portfolioVar());
        assertTrue(report95.portfolioExpectedShortfall().doubleValue()
                >= report95.portfolioVar().doubleValue() - 0.01);
        assertTrue(report99.portfolioVar().doubleValue() >= report95.portfolioVar().doubleValue() - 0.01);
    }

    @Test
    @DisplayName("历史样本不足: NOT_COMPUTABLE, 区别于结果为零的空仓")
    void insufficientHistoryIsNotComputable() {
        // 5 prices -> 4 returns; at 95% the 5% worst tail needs ceil(0.05*4)=1 loss
        // observation drawn from strictly more than 4 returns -> deliberately insufficient.
        market.publishHistory("AAA", List.of(new BigDecimal("10"), new BigDecimal("10.1"),
                new BigDecimal("9.9"), new BigDecimal("10.2"), new BigDecimal("10.0")));
        RiskReport report = risk.report(acct, new BigDecimal("0.99"));
        assertEquals(RiskStatus.NOT_COMPUTABLE, report.positions().get(0).status());
        assertTrue(report.notComputableSymbols().contains("AAA"));
        assertEquals(0, report.portfolioVar().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("无历史数据: NOT_COMPUTABLE 而不是零风险")
    void noHistoryIsNotComputable() {
        TestFixtures.instrument(instruments, "NEW1", 100, true);
        TestFixtures.freshQuote(market, clockValue::get, "NEW1", "8.00");
        var order = trading.submit(acct, new OrderRequest("r-buy2", Side.BUY, "NEW1", 100, null));
        trading.fill(acct, order.getOrderId());
        RiskReport report = risk.report(acct, null);
        var line = report.positions().stream().filter(p -> p.symbol().equals("NEW1")).findFirst().orElseThrow();
        assertEquals(RiskStatus.NOT_COMPUTABLE, line.status());
    }

    @Test
    @DisplayName("非法置信水平: 参数校验失败")
    void invalidConfidenceRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> risk.report(acct, new BigDecimal("1.5")));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getCode());
    }
}
