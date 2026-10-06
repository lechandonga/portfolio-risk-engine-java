package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.AccountLockManager;
import com.github.lechandonga.portfolioriskenginejava.domain.Instrument;
import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderStatus;
import com.github.lechandonga.portfolioriskenginejava.domain.Position;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataService;
import com.github.lechandonga.portfolioriskenginejava.market.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Orders are two-phase: submit reserves cash/shares against the current quote (NEW),
 * fill consumes the reservation at the then-current quote, cancel releases it.
 * Every state transition happens under the account lock, so concurrent
 * submit/fill/cancel/refresh interleavings serialise per account.
 */
@Service
public class TradingService {
    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final PositionRepository positions;
    private final OrderRepository orders;
    private final LedgerRepository ledger;
    private final MarketDataService market;
    private final FeeService fees;
    private final AccountLockManager locks;
    private final LongSupplier clock;

    public TradingService(AccountRepository accounts, InstrumentRepository instruments,
                          PositionRepository positions, OrderRepository orders, LedgerRepository ledger,
                          MarketDataService market, FeeService fees, AccountLockManager locks,
                          LongSupplier clock) {
        this.accounts = accounts;
        this.instruments = instruments;
        this.positions = positions;
        this.orders = orders;
        this.ledger = ledger;
        this.market = market;
        this.fees = fees;
        this.locks = locks;
        this.clock = clock;
    }

    public Order submit(String accountId, OrderRequest req) {
        if (req.quantity() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "quantity must be positive");
        }
        Account account = requireAccount(accountId);
        Instrument instrument = requireTradable(req.symbol());
        if (req.quantity() % instrument.getLotSize() != 0) {
            throw new BusinessException(ErrorCode.QUANTITY_NOT_MULTIPLE_OF_LOT,
                    "quantity " + req.quantity() + " is not a multiple of lot size "
                            + instrument.getLotSize() + " for " + instrument.getSymbol());
        }

        synchronized (locks.lockFor(accountId)) {
            // Idempotency: a retried clientRequestId replays the exact original outcome.
            Order existing = orders.findByIdempotencyKey(accountId, req.clientRequestId());
            if (existing != null) {
                log.info("idempotent replay account={} clientRequestId={} order={} status={}",
                        accountId, req.clientRequestId(), existing.getOrderId(), existing.getStatus());
                return existing;
            }

            Quote quote = market.requireFreshQuote(req.symbol());
            if (req.limitPrice() != null) {
                checkPriceBand(instrument, quote.price(), req.limitPrice());
            }

            Order order = new Order(UUID.randomUUID().toString(), req.clientRequestId(), accountId,
                    req.symbol(), req.side(), req.quantity(), req.limitPrice(), clock.getAsLong());
            BigDecimal notional = quote.price().multiply(BigDecimal.valueOf(req.quantity()));

            if (req.side() == Side.BUY) {
                BigDecimal estimatedFee = fees.fee(Side.BUY, quote.price(), req.quantity());
                BigDecimal need = notional.add(estimatedFee);
                if (account.availableCash().compareTo(need) < 0) {
                    order.setStatus(OrderStatus.REJECTED);
                    order.setRejectReason("INSUFFICIENT_CASH");
                    orders.save(order);
                    throw new BusinessException(ErrorCode.INSUFFICIENT_CASH,
                            "need " + need.setScale(2, RoundingMode.HALF_UP)
                                    + " but only " + account.availableCash().setScale(2, RoundingMode.HALF_UP)
                                    + " available");
                }
                account.setReservedCash(account.getReservedCash().add(need));
                order.setReservedAmount(need);
            } else {
                Position position = positions.get(accountId, req.symbol());
                long available = position == null ? 0 : position.availableQuantity();
                if (available < req.quantity()) {
                    order.setStatus(OrderStatus.REJECTED);
                    order.setRejectReason("INSUFFICIENT_QUANTITY");
                    orders.save(order);
                    throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY,
                            "need " + req.quantity() + " available shares but hold " + available);
                }
                position.setReservedQuantity(position.getReservedQuantity() + req.quantity());
            }
            orders.save(order);
            log.info("order accepted account={} order={} side={} symbol={} qty={}",
                    accountId, order.getOrderId(), req.side(), req.symbol(), req.quantity());
            return order;
        }
    }

    public Order cancel(String accountId, String orderId) {
        requireAccount(accountId);
        synchronized (locks.lockFor(accountId)) {
            Order order = requireOrderOf(accountId, orderId);
            if (order.getStatus() != OrderStatus.NEW) {
                throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE,
                        "order " + orderId + " is " + order.getStatus() + ", only NEW orders can be cancelled");
            }
            releaseReservation(accountId, order);
            order.setStatus(OrderStatus.CANCELLED);
            log.info("order cancelled account={} order={}", accountId, orderId);
            return order;
        }
    }

    public Order fill(String accountId, String orderId) {
        requireAccount(accountId);
        synchronized (locks.lockFor(accountId)) {
            Order order = requireOrderOf(accountId, orderId);
            if (order.getStatus() == OrderStatus.FILLED) {
                throw new BusinessException(ErrorCode.ORDER_ALREADY_FINAL,
                        "order " + orderId + " is already FILLED");
            }
            if (order.getStatus() != OrderStatus.NEW) {
                throw new BusinessException(ErrorCode.ORDER_NOT_FILLABLE,
                        "order " + orderId + " is " + order.getStatus() + ", only NEW orders can be filled");
            }
            Account account = accounts.get(accountId);
            Instrument instrument = requireTradable(order.getSymbol());
            Quote quote = market.requireFreshQuote(order.getSymbol());
            if (order.getLimitPrice() != null) {
                checkPriceBand(instrument, quote.price(), order.getLimitPrice());
            }

            BigDecimal price = quote.price();
            BigDecimal fee = fees.fee(order.getSide(), price, order.getQuantity());
            BigDecimal notional = price.multiply(BigDecimal.valueOf(order.getQuantity()));

            if (order.getSide() == Side.BUY) {
                BigDecimal totalDue = notional.add(fee);
                // The submit-time reservation covers the bill (with market-price drift, any excess
                // stays in cash; a shortfall can only occur with concurrent external debits).
                releaseReservation(accountId, order);
                if (account.availableCash().compareTo(totalDue) < 0) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_CASH,
                            "fill requires " + totalDue.setScale(2, RoundingMode.HALF_UP)
                                    + " available cash, have "
                                    + account.availableCash().setScale(2, RoundingMode.HALF_UP));
                }
                account.setCash(account.getCash().subtract(notional).subtract(fee));
                Position position = positions.getOrCreate(accountId, order.getSymbol());
                // Cost basis tracks acquisition price only; commission is an expense, never P&L.
                position.setCostBasis(position.getCostBasis().add(notional));
                position.setQuantity(position.getQuantity() + order.getQuantity());
                // BUY records the actual cash outflow (notional incl. fee); FEE_DETAIL is
                // informational and excluded from the cash sum.
                ledger.append(accountId, "BUY", order.getOrderId(), totalDue.negate(), clock.getAsLong());
                ledger.append(accountId, "FEE_DETAIL", order.getOrderId(), fee.negate(), clock.getAsLong());
            } else {
                releaseReservation(accountId, order);
                Position position = positions.get(accountId, order.getSymbol());
                BigDecimal costOfSold = position.removeCost(order.getQuantity());
                BigDecimal proceeds = notional.subtract(fee);
                BigDecimal realized = proceeds.subtract(costOfSold);
                account.setCash(account.getCash().add(proceeds));
                account.setRealizedPnl(account.getRealizedPnl().add(realized));
                position.setQuantity(position.getQuantity() - order.getQuantity());
                // SELL records net cash received; FEE_DETAIL is informational (not part of sum).
                ledger.append(accountId, "SELL", order.getOrderId(), proceeds, clock.getAsLong());
                ledger.append(accountId, "FEE_DETAIL", order.getOrderId(), fee.negate(), clock.getAsLong());
            }
            order.setStatus(OrderStatus.FILLED);
            order.setFilledPrice(price);
            order.setFee(fee);
            order.setFilledAt(clock.getAsLong());
            log.info("order filled account={} order={} price={} fee={}", accountId, orderId, price, fee);
            return order;
        }
    }

    private void releaseReservation(String accountId, Order order) {
        Account account = accounts.get(accountId);
        if (order.getSide() == Side.BUY) {
            account.setReservedCash(account.getReservedCash().subtract(order.getReservedAmount()));
        } else {
            Position position = positions.get(accountId, order.getSymbol());
            if (position != null) {
                position.setReservedQuantity(position.getReservedQuantity() - order.getQuantity());
            }
        }
    }

    private void checkPriceBand(Instrument instrument, BigDecimal reference, BigDecimal limit) {
        BigDecimal band = reference.multiply(instrument.getMaxPriceDeviation());
        if (limit.subtract(reference).abs().compareTo(band) > 0) {
            throw new BusinessException(ErrorCode.PRICE_OUT_OF_BAND,
                    "limit " + limit + " deviates more than " + instrument.getMaxPriceDeviation()
                            + " from reference " + reference + " for " + instrument.getSymbol());
        }
    }

    private Account requireAccount(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + accountId);
        }
        return account;
    }

    private Instrument requireTradable(String symbol) {
        Instrument instrument = instruments.get(symbol);
        if (instrument == null) {
            throw new BusinessException(ErrorCode.UNKNOWN_INSTRUMENT, "unknown instrument: " + symbol);
        }
        if (!instrument.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_INSTRUMENT,
                    "instrument " + symbol + " is not active for trading");
        }
        return instrument;
    }

    private Order requireOrderOf(String accountId, String orderId) {
        Order order = orders.get(orderId);
        if (order == null || !order.getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND,
                    "order not found for account " + accountId + ": " + orderId);
        }
        return order;
    }
}
