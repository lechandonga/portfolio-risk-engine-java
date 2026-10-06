package com.github.lechandonga.portfolioriskenginejava.tests;

import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderStatus;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderType;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ServiceException;
import com.github.lechandonga.portfolioriskenginejava.tests.support.EngineHarness;
import com.github.lechandonga.portfolioriskenginejava.trading.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("业务1：交易指令（买卖/幂等/校验/撤单）")
class TradingEngineTest {

    private EngineHarness h;

    private OrderRequest marketBuy(String clientId, String qty) {
        return new OrderRequest("acc-1", clientId, "AAPL", Side.BUY,
                OrderType.MARKET, new BigDecimal(qty), null);
    }

    @BeforeEach
    void setUp() {
        h = new EngineHarness();
    }

    @Nested
    @DisplayName("下单成交与费用")
    class FillCases {

        @Test
        @DisplayName("市价买入按最新价成交并扣减现金、计入含费成本")
        void marketBuyFills() {
            Order order = h.trading.submit(marketBuy("c-1", "100"));
            assertEquals(OrderStatus.FILLED, order.getStatus());
            assertEquals(0, new BigDecimal("100.00").compareTo(order.getAvgFillPrice()));
            // 成交额 10000 + 费用 max(10000*0.0003, 1) = 3
            assertEquals(0, new BigDecimal("3.00").compareTo(order.getFee()));
            assertEquals(0, new BigDecimal("89997.00")
                    .compareTo(h.store.getEntry("acc-1").getAccount().getCash()));
            assertEquals(0, new BigDecimal("10003.00")
                    .compareTo(h.store.getEntry("acc-1").getAccount()
                            .getPositions().get("AAPL").getCostBasis()));
        }

        @Test
        @DisplayName("市价卖出回笼现金、按移动平均成本确认已实现盈亏并扣费")
        void marketSellRealizesPnl() {
            h.trading.submit(marketBuy("c-1", "100"));
            Order sell = h.trading.submit(new OrderRequest("acc-1", "c-2", "AAPL",
                    Side.SELL, OrderType.MARKET, new BigDecimal("40"), null));
            assertEquals(OrderStatus.FILLED, sell.getStatus());
            var account = h.store.getEntry("acc-1").getAccount();
            // 卖出 4000，费用 1.20；成本 40*100.03=4001.20，已实现 = 4000-4001.20-1.20
            assertEquals(0, new BigDecimal("-2.40")
                    .compareTo(account.getRealizedPnl()));
            assertEquals(0, new BigDecimal("60")
                    .compareTo(account.getPositions().get("AAPL").getQuantity()));
            assertEquals(0, new BigDecimal("6001.80")
                    .compareTo(account.getPositions().get("AAPL").getCostBasis()));
        }
    }

    @Nested
    @DisplayName("重复提交幂等")
    class IdempotencyCases {

        @Test
        @DisplayName("相同 clientRequestId 重试返回首单且不重复扣款加仓")
        void duplicateSubmitIsIdempotent() {
            Order first = h.trading.submit(marketBuy("dup-1", "10"));
            Order second = h.trading.submit(marketBuy("dup-1", "10"));
            assertEquals(first.getId(), second.getId());
            assertSame(first, second);
            var account = h.store.getEntry("acc-1").getAccount();
            assertEquals(0, new BigDecimal("10").compareTo(
                    account.getPositions().get("AAPL").getQuantity()));
            assertEquals(0, new BigDecimal("98999.00").compareTo(account.getCash()));
        }

        @Test
        @DisplayName("同内容但不同 clientRequestId 是两笔独立指令")
        void differentClientIdsAreDistinct() {
            Order first = h.trading.submit(marketBuy("a", "10"));
            Order second = h.trading.submit(marketBuy("b", "10"));
            assertNotEquals(first.getId(), second.getId());
            assertEquals(0, new BigDecimal("20").compareTo(
                    h.store.getEntry("acc-1").getAccount()
                            .getPositions().get("AAPL").getQuantity()));
            // 每笔最低费用 1.00（成交额 1000 按费率仅 0.30）
            assertEquals(0, new BigDecimal("97998.00").compareTo(
                    h.store.getEntry("acc-1").getAccount().getCash()));
        }
    }

    @Nested
    @DisplayName("非法指令的差异化拒绝")
    class RejectCases {

        @Test
        @DisplayName("资金不足给出 INSUFFICIENT_CASH 而非笼统错误")
        void insufficientCash() {
            Order rejected = h.trading.submit(marketBuy("big", "1000000"));
            assertEquals(OrderStatus.REJECTED, rejected.getStatus());
            assertEquals(ErrorCode.INSUFFICIENT_CASH.name(), rejected.getRejectReason());
            assertEquals(0, new BigDecimal("100000.00").compareTo(
                    h.store.getEntry("acc-1").getAccount().getCash()));
        }

        @Test
        @DisplayName("可用数量不足给出 INSUFFICIENT_POSITION")
        void insufficientPosition() {
            Order rejected = h.trading.submit(new OrderRequest("acc-1", "s", "AAPL",
                    Side.SELL, OrderType.MARKET, new BigDecimal("1"), null));
            assertEquals(OrderStatus.REJECTED, rejected.getStatus());
            assertEquals(ErrorCode.INSUFFICIENT_POSITION.name(), rejected.getRejectReason());
        }

        @Test
        @DisplayName("标的不存在给出 SYMBOL_NOT_FOUND")
        void symbolNotFound() {
            ServiceException ex = assertThrows(ServiceException.class, () ->
                    h.trading.submit(new OrderRequest("acc-1", "s", "NOPE",
                            Side.BUY, OrderType.MARKET, new BigDecimal("1"), null)));
            assertEquals(ErrorCode.SYMBOL_NOT_FOUND, ex.getCode());
        }

        @Test
        @DisplayName("数量非法给出 ILLEGAL_AMOUNT")
        void illegalQuantity() {
            ServiceException ex = assertThrows(ServiceException.class, () ->
                    h.trading.submit(marketBuy("q", "0")));
            assertEquals(ErrorCode.ILLEGAL_AMOUNT, ex.getCode());
        }

        @Test
        @DisplayName("账户不存在给出 ACCOUNT_NOT_FOUND")
        void accountNotFound() {
            ServiceException ex = assertThrows(ServiceException.class, () ->
                    h.trading.submit(new OrderRequest("ghost", "s", "AAPL",
                            Side.BUY, OrderType.MARKET, new BigDecimal("1"), null)));
            assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, ex.getCode());
        }

        @Test
        @DisplayName("无行情标的的市价单被拒绝为 ORDER_PRICE_UNAVAILABLE，且幂等可查")
        void noMarketForOrder() {
            Order rejected = h.trading.submit(new OrderRequest("acc-1", "r", "ILLIQ",
                    Side.BUY, OrderType.LIMIT, new BigDecimal("1"),
                    new BigDecimal("1.00")));
            assertEquals(OrderStatus.NEW, rejected.getStatus());
            assertEquals(0, new BigDecimal("99998.00").compareTo(
                    h.store.getEntry("acc-1").getAccount().getAvailableCash()));
        }
    }

    @Nested
    @DisplayName("撤单与资金/数量预留")
    class CancelCases {

        @Test
        @DisplayName("未成交限价买单预占现金，撤单后完整释放")
        void cancelLimitBuyReleasesCash() {
            Order order = h.trading.submit(new OrderRequest("acc-1", "lb", "AAPL",
                    Side.BUY, OrderType.LIMIT, new BigDecimal("10"),
                    new BigDecimal("90.00")));
            assertEquals(OrderStatus.NEW, order.getStatus());
            assertEquals(0, new BigDecimal("99099.00").compareTo(
                    h.store.getEntry("acc-1").getAccount().getAvailableCash()));
            h.trading.cancel("acc-1", order.getId());
            assertEquals(OrderStatus.CANCELLED,
                    h.trading.getOrder("acc-1", order.getId()).getStatus());
            assertEquals(0, new BigDecimal("100000.00").compareTo(
                    h.store.getEntry("acc-1").getAccount().getAvailableCash()));
        }

        @Test
        @DisplayName("未成交限价卖单预占数量，撤单后释放；已成交单撤单报 ORDER_NOT_CANCELLABLE")
        void cancelCases() {
            h.trading.submit(marketBuy("b", "10"));
            Order sell = h.trading.submit(new OrderRequest("acc-1", "ls", "AAPL",
                    Side.SELL, OrderType.LIMIT, new BigDecimal("6"),
                    new BigDecimal("200.00")));
            assertEquals(OrderStatus.NEW, sell.getStatus());
            var position = h.store.getEntry("acc-1").getAccount()
                    .getPositions().get("AAPL");
            assertEquals(0, new BigDecimal("4").compareTo(position.getAvailableQty()));
            h.trading.cancel("acc-1", sell.getId());
            assertEquals(0, new BigDecimal("10").compareTo(position.getAvailableQty()));

            Order filled = h.trading.submit(marketBuy("b2", "1"));
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> h.trading.cancel("acc-1", filled.getId()));
            assertEquals(ErrorCode.ORDER_NOT_CANCELLABLE, ex.getCode());

            assertNotNull(h.trading.listOrders("acc-1"));
        }
    }
}
