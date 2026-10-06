package com.github.lechandonga.portfolioriskenginejava.trading;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderStatus;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderType;
import com.github.lechandonga.portfolioriskenginejava.domain.Position;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ServiceException;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataException;
import com.github.lechandonga.portfolioriskenginejava.market.Quote;
import com.github.lechandonga.portfolioriskenginejava.market.SimClock;
import com.github.lechandonga.portfolioriskenginejava.market.ValidatingMarketDataProvider;
import com.github.lechandonga.portfolioriskenginejava.money.Money;
import com.github.lechandonga.portfolioriskenginejava.store.AccountEntry;
import com.github.lechandonga.portfolioriskenginejava.store.AccountStore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 交易引擎。
 *
 * 语义约定：
 * - clientRequestId 是调用方幂等键：相同键的重复提交/重试返回首次结果，绝不重复扣款加仓；
 * - MARKET 单按最新有效行情立即成交，无行情（含过期）→ ORDER_PRICE_UNAVAILABLE；
 * - LIMIT 单：价格可成交则立即成交；买单预占现金（availableCash 减少），
 *   卖单预占数量（availableQty 减少），撤单时释放；
 * - 资金不足/数量不足/标的不存在分别给出独立错误码。
 */
public class TradingEngine {

    private final AccountStore store;
    private final ValidatingMarketDataProvider marketData;
    private final FeePolicy feePolicy;
    private final SimClock clock;
    private final AtomicLong orderIdSeq = new AtomicLong();

    public TradingEngine(AccountStore store, ValidatingMarketDataProvider marketData,
                         FeePolicy feePolicy, SimClock clock) {
        this.store = store;
        this.marketData = marketData;
        this.feePolicy = feePolicy;
        this.clock = clock;
    }

    public Order submit(OrderRequest req) {
        validateRequest(req);
        return store.withAccount(req.accountId(), entry -> {
            Order existing = findByClientRequest(entry, req.clientRequestId());
            if (existing != null) {
                return existing;
            }

            Order order = new Order(nextOrderId(), req.accountId(), req.clientRequestId(),
                    req.symbol(), req.side(), req.type(), Money.qty(req.quantity()),
                    req.limitPrice() == null ? null : Money.money(req.limitPrice()),
                    clock.now());
            entry.getOrders().put(order.getId(), order);
            entry.getOrderIdsByClientRequest().put(req.clientRequestId(), order.getId());

            try {
                Quote quote = marketData.freshQuote(req.symbol());
                if (req.type() == OrderType.MARKET) {
                    if (quote == null) {
                        reject(order, entry, ErrorCode.ORDER_PRICE_UNAVAILABLE);
                        return order;
                    }
                    fill(entry, order, quote.lastPrice());
                } else {
                    if (isMarketable(req.side(), req.limitPrice(), quote)) {
                        fill(entry, order, quote.lastPrice());
                    } else {
                        reserve(entry, order);
                    }
                }
            } catch (MarketDataException e) {
                reject(order, entry, mapMarketFailure(e));
            }
            return order;
        });
    }

    public Order cancel(String accountId, long orderId) {
        return store.withAccount(accountId, entry -> {
            Order order = entry.getOrders().get(orderId);
            if (order == null) {
                throw new ServiceException(ErrorCode.ORDER_NOT_FOUND, String.valueOf(orderId));
            }
            if (order.getStatus() != OrderStatus.NEW) {
                throw new ServiceException(ErrorCode.ORDER_NOT_CANCELLABLE,
                        "当前状态=" + order.getStatus());
            }
            releaseReservation(entry, order);
            order.setStatus(OrderStatus.CANCELLED);
            return order;
        });
    }

    public Order getOrder(String accountId, long orderId) {
        return store.withAccount(accountId, entry -> {
            Order order = entry.getOrders().get(orderId);
            if (order == null) {
                throw new ServiceException(ErrorCode.ORDER_NOT_FOUND, String.valueOf(orderId));
            }
            return order;
        });
    }

    public List<Order> listOrders(String accountId) {
        return store.withAccount(accountId, entry ->
                entry.getOrders().values().stream()
                        .sorted(Comparator.comparingLong(Order::getId))
                        .toList());
    }

    // ---- internals -------------------------------------------------------

    private void fill(AccountEntry entry, Order order, BigDecimal rawPrice) {
        BigDecimal price = Money.money(rawPrice);
        BigDecimal quantity = Money.qty(order.getQuantity());
        BigDecimal notional = Money.money(price.multiply(quantity));
        BigDecimal fee = feePolicy.fee(notional);
        Account account = entry.getAccount();
        Position position = account.getPositions()
                .computeIfAbsent(order.getSymbol(), Position::new);

        if (order.getSide() == Side.BUY) {
            if (account.getAvailableCash().compareTo(notional.add(fee)) < 0) {
                reject(order, entry, ErrorCode.INSUFFICIENT_CASH);
                return;
            }
            account.setCash(Money.money(account.getCash().subtract(notional).subtract(fee)));
            account.setAvailableCash(Money.money(
                    account.getAvailableCash().subtract(notional).subtract(fee)));
            position.setQuantity(Money.qty(position.getQuantity().add(quantity)));
            position.setAvailableQty(Money.qty(position.getAvailableQty().add(quantity)));
            position.setCostBasis(Money.money(position.getCostBasis().add(notional).add(fee)));
        } else {
            if (position.getAvailableQty().compareTo(quantity) < 0) {
                reject(order, entry, ErrorCode.INSUFFICIENT_POSITION);
                return;
            }
            BigDecimal avgCost = position.getQuantity().signum() == 0
                    ? BigDecimal.ZERO
                    : position.getCostBasis()
                        .divide(position.getQuantity(), Money.MONEY_SCALE, java.math.RoundingMode.HALF_UP);
            BigDecimal costOut = Money.money(avgCost.multiply(quantity));
            BigDecimal realizedDelta = notional.subtract(costOut).subtract(fee);

            account.setCash(Money.money(account.getCash().add(notional).subtract(fee)));
            account.setAvailableCash(Money.money(
                    account.getAvailableCash().add(notional).subtract(fee)));
            account.setRealizedPnl(Money.money(account.getRealizedPnl().add(realizedDelta)));
            position.setQuantity(Money.qty(position.getQuantity().subtract(quantity)));
            position.setAvailableQty(Money.qty(position.getAvailableQty().subtract(quantity)));
            position.setCostBasis(Money.money(position.getCostBasis().subtract(costOut)));
            if (position.getQuantity().signum() == 0) {
                position.setCostBasis(BigDecimal.ZERO.setScale(Money.MONEY_SCALE));
            }
        }
        order.setStatus(OrderStatus.FILLED);
        order.setAvgFillPrice(price);
        order.setFee(fee);
        order.setFilledAt(clock.now());
    }

    private void reserve(AccountEntry entry, Order order) {
        Account account = entry.getAccount();
        BigDecimal quantity = Money.qty(order.getQuantity());
        if (order.getSide() == Side.BUY) {
            BigDecimal need = Money.money(order.getLimitPrice().multiply(quantity));
            BigDecimal estimatedFee = feePolicy.fee(need);
            if (account.getAvailableCash().compareTo(need.add(estimatedFee)) < 0) {
                reject(order, entry, ErrorCode.INSUFFICIENT_CASH);
                return;
            }
            account.setAvailableCash(Money.money(
                    account.getAvailableCash().subtract(need).subtract(estimatedFee)));
        } else {
            Position position = account.getPositions().get(order.getSymbol());
            if (position == null || position.getAvailableQty().compareTo(quantity) < 0) {
                reject(order, entry, ErrorCode.INSUFFICIENT_POSITION);
                return;
            }
            position.setAvailableQty(Money.qty(position.getAvailableQty().subtract(quantity)));
        }
    }

    private void releaseReservation(AccountEntry entry, Order order) {
        if (order.getStatus() != OrderStatus.NEW) {
            return;
        }
        Account account = entry.getAccount();
        BigDecimal quantity = Money.qty(order.getQuantity());
        if (order.getSide() == Side.BUY) {
            BigDecimal reserved = Money.money(order.getLimitPrice().multiply(quantity));
            BigDecimal estimatedFee = feePolicy.fee(reserved);
            account.setAvailableCash(Money.money(
                    account.getAvailableCash().add(reserved).add(estimatedFee)));
        } else {
            Position position = account.getPositions().get(order.getSymbol());
            if (position != null) {
                position.setAvailableQty(Money.qty(position.getAvailableQty().add(quantity)));
            }
        }
    }

    private void reject(Order order, AccountEntry entry, ErrorCode code) {
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectReason(code.name());
        order.setFilledAt(clock.now());
    }

    private boolean isMarketable(Side side, BigDecimal limitPrice, Quote quote) {
        if (quote == null) {
            return false;
        }
        if (side == Side.BUY) {
            return quote.lastPrice().compareTo(limitPrice) <= 0;
        }
        return quote.lastPrice().compareTo(limitPrice) >= 0;
    }

    private Order findByClientRequest(AccountEntry entry, String clientRequestId) {
        Long id = entry.getOrderIdsByClientRequest().get(clientRequestId);
        return id == null ? null : entry.getOrders().get(id);
    }

    private ErrorCode mapMarketFailure(MarketDataException e) {
        return switch (e.getKind()) {
            case TIMEOUT -> ErrorCode.MARKET_DATA_TIMEOUT;
            case DIRTY -> ErrorCode.MARKET_DATA_DIRTY;
            case UNAVAILABLE -> ErrorCode.MARKET_DATA_UNAVAILABLE;
        };
    }

    private void validateRequest(OrderRequest req) {
        if (req.accountId() == null || req.accountId().isBlank()
                || req.clientRequestId() == null || req.clientRequestId().isBlank()
                || req.symbol() == null || req.symbol().isBlank()
                || req.side() == null || req.type() == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "缺少必填字段");
        }
        if (!Money.positive(req.quantity())) {
            throw new ServiceException(ErrorCode.ILLEGAL_AMOUNT, "quantity 必须为正数");
        }
        if (req.type() == OrderType.LIMIT && !Money.positive(req.limitPrice())) {
            throw new ServiceException(ErrorCode.ILLEGAL_AMOUNT, "limitPrice 必须为正数");
        }
        if (!marketData.symbols().contains(req.symbol())) {
            throw new ServiceException(ErrorCode.SYMBOL_NOT_FOUND, req.symbol());
        }
    }

    private long nextOrderId() {
        return orderIdSeq.incrementAndGet();
    }

    public Instant now() {
        return clock.now();
    }
}
