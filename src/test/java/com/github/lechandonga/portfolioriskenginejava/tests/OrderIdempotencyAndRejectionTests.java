package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderStatus;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.market.LocalMarketDataSource;
import com.github.lechandonga.portfolioriskenginejava.service.AccountRepository;
import com.github.lechandonga.portfolioriskenginejava.service.InstrumentRepository;
import com.github.lechandonga.portfolioriskenginejava.service.LedgerRepository;
import com.github.lechandonga.portfolioriskenginejava.service.OrderRepository;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ControllableClockConfig.class)
class OrderIdempotencyAndRejectionTests extends BusinessTestBase {
    @Autowired TradingService trading;
    @Autowired AccountRepository accounts;
    @Autowired InstrumentRepository instruments;
    @Autowired PositionRepository positions;
    @Autowired OrderRepository orders;
    @Autowired LedgerRepository ledger;
    @Autowired LocalMarketDataSource market;
    @Autowired AtomicLong clockValue;

    private String acct;

    @Override
    protected String businessModule() {
        return "交易指令-重复提交与拒绝原因";
    }

    @BeforeEach
    void setup() {
        acct = "ord-" + UUID.randomUUID();
        market.clearFault();
        market.clearFault();
        TestFixtures.account(accounts, ledger, clockValue::get, acct, "100000");
        TestFixtures.instrument(instruments, "AAA", 100, true);
        TestFixtures.freshQuote(market, clockValue::get, "AAA", "10.00");
    }

    @Test
    @DisplayName("相同 clientRequestId 重试: 只生成一张单, 只冻结一次资金")
    void duplicateClientRequestIdSubmitsOnce() {
        OrderRequest req = new OrderRequest("idem-1", Side.BUY, "AAA", 1000, null);
        Order first = trading.submit(acct, req);
        BigDecimal cashAfterFirst = accounts.get(acct).getReservedCash();
        Order second = trading.submit(acct, req);
        assertEquals(first.getOrderId(), second.getOrderId(), "重试必须返回同一张订单");
        assertEquals(cashAfterFirst, accounts.get(acct).getReservedCash(), "重试不能重复冻结资金");
        assertEquals(1, orders.findByAccount(acct).size());
    }

    @Test
    @DisplayName("资金不足: 返回 INSUFFICIENT_CASH, 订单落为 REJECTED")
    void insufficientCashIsRejectedWithDistinctCode() {
        OrderRequest req = new OrderRequest("poor-1", Side.BUY, "AAA", 10_000_000, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> trading.submit(acct, req));
        assertEquals(ErrorCode.INSUFFICIENT_CASH, ex.getCode());
        assertEquals(BigDecimal.ZERO, accounts.get(acct).getReservedCash(), "被拒单不能冻结资金");
    }

    @Test
    @DisplayName("可用数量不足: 卖空被拒 INSUFFICIENT_QUANTITY, 与资金不足区分")
    void insufficientQuantityHasOwnCode() {
        OrderRequest req = new OrderRequest("short-1", Side.SELL, "AAA", 100, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> trading.submit(acct, req));
        assertEquals(ErrorCode.INSUFFICIENT_QUANTITY, ex.getCode());
    }

    @Test
    @DisplayName("标的不存在/停牌: 分别返回 UNKNOWN_INSTRUMENT / INACTIVE_INSTRUMENT")
    void unknownAndInactiveInstrumentCodesDiffer() {
        BusinessException unknown = assertThrows(BusinessException.class, () ->
                trading.submit(acct, new OrderRequest("u1", Side.BUY, "ZZZ", 100, null)));
        assertEquals(ErrorCode.UNKNOWN_INSTRUMENT, unknown.getCode());

        TestFixtures.instrument(instruments, "DEAD", 100, false);
        TestFixtures.freshQuote(market, clockValue::get, "DEAD", "5.00");
        BusinessException inactive = assertThrows(BusinessException.class, () ->
                trading.submit(acct, new OrderRequest("d1", Side.BUY, "DEAD", 100, null)));
        assertEquals(ErrorCode.INACTIVE_INSTRUMENT, inactive.getCode());
    }

    @Test
    @DisplayName("数量不是整手: 返回 QUANTITY_NOT_MULTIPLE_OF_LOT")
    void oddLotRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                trading.submit(acct, new OrderRequest("lot-1", Side.BUY, "AAA", 50, null)));
        assertEquals(ErrorCode.QUANTITY_NOT_MULTIPLE_OF_LOT, ex.getCode());
    }

    @Test
    @DisplayName("撤单释放冻结; 终态订单不能重复撤/重复成交")
    void cancelReleasesReservationAndTerminalTransitionsRejected() {
        Order order = trading.submit(acct, new OrderRequest("c1", Side.BUY, "AAA", 1000, null));
        trading.cancel(acct, order.getOrderId());
        assertEquals(0, accounts.get(acct).getReservedCash().compareTo(BigDecimal.ZERO));

        BusinessException cancelAgain = assertThrows(BusinessException.class,
                () -> trading.cancel(acct, order.getOrderId()));
        assertEquals(ErrorCode.ORDER_NOT_CANCELLABLE, cancelAgain.getCode());

        Order other = trading.submit(acct, new OrderRequest("c2", Side.BUY, "AAA", 100, null));
        trading.fill(acct, other.getOrderId());
        BusinessException fillAgain = assertThrows(BusinessException.class,
                () -> trading.fill(acct, other.getOrderId()));
        assertEquals(ErrorCode.ORDER_ALREADY_FINAL, fillAgain.getCode());
    }

    @Test
    @DisplayName("买入成交: 现金按 价款+费用 扣减, 持仓与成本正确")
    void buyFillDebitsExactlyOnceWithFee() {
        BigDecimal before = accounts.get(acct).getCash();
        Order order = trading.submit(acct, new OrderRequest("b1", Side.BUY, "AAA", 1000, null));
        trading.fill(acct, order.getOrderId());
        Order filled = orders.get(order.getOrderId());
        assertEquals(OrderStatus.FILLED, filled.getStatus());
        BigDecimal expectedCash = before.subtract(new BigDecimal("10000.00")).subtract(filled.getFee());
        assertEquals(0, expectedCash.compareTo(accounts.get(acct).getCash()));
        assertEquals(1000, positions.get(acct, "AAA").getQuantity());
        assertEquals(0, new BigDecimal("10000.00").compareTo(positions.get(acct, "AAA").getCostBasis()),
                "买入费用不得计入持仓成本");
        assertEquals(0, accounts.get(acct).getReservedCash().compareTo(BigDecimal.ZERO));
    }
}
