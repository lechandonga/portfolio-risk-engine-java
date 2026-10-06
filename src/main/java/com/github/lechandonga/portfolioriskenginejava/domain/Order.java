package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** 交易指令。成交后保留成交价、费用，便于审计与幂等重试返回一致结果。 */
public class Order {

    private final long id;
    private final String accountId;
    private final String clientRequestId;
    private final String symbol;
    private final Side side;
    private final OrderType type;
    private final BigDecimal quantity;
    private final BigDecimal limitPrice;
    private final Instant createdAt;

    private volatile OrderStatus status;
    private volatile BigDecimal avgFillPrice;
    private volatile BigDecimal fee;
    private volatile Instant filledAt;
    private volatile String rejectReason;

    public Order(long id, String accountId, String clientRequestId, String symbol, Side side,
                 OrderType type, BigDecimal quantity, BigDecimal limitPrice, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.clientRequestId = clientRequestId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.createdAt = createdAt;
        this.status = OrderStatus.NEW;
    }

    public long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getClientRequestId() { return clientRequestId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public OrderType getType() { return type; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public Instant getCreatedAt() { return createdAt; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getAvgFillPrice() { return avgFillPrice; }
    public void setAvgFillPrice(BigDecimal avgFillPrice) { this.avgFillPrice = avgFillPrice; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public Instant getFilledAt() { return filledAt; }
    public void setFilledAt(Instant filledAt) { this.filledAt = filledAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
