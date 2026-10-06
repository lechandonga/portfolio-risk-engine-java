package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;

public class Order {
    private final String orderId;
    /** Caller-supplied idempotency key; unique per account. Retries replay the original result. */
    private final String clientRequestId;
    private final String accountId;
    private final String symbol;
    private final Side side;
    private final long quantity;
    /** Optional limit price used for the price-band check at submit and fill. */
    private final BigDecimal limitPrice;
    private OrderStatus status;
    private BigDecimal filledPrice;
    private BigDecimal fee;
    /** Cash blocked at submit time for BUY (notional + fee at submit quote). Zero for SELL. */
    private BigDecimal reservedAmount = BigDecimal.ZERO;
    private final long createdAt;
    private Long filledAt;
    private String rejectReason;

    public Order(String orderId, String clientRequestId, String accountId, String symbol, Side side,
                 long quantity, BigDecimal limitPrice, long createdAt) {
        this.orderId = orderId;
        this.clientRequestId = clientRequestId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.createdAt = createdAt;
        this.status = OrderStatus.NEW;
    }

    public String getOrderId() { return orderId; }
    public String getClientRequestId() { return clientRequestId; }
    public String getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public long getQuantity() { return quantity; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getFilledPrice() { return filledPrice; }
    public void setFilledPrice(BigDecimal filledPrice) { this.filledPrice = filledPrice; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public long getCreatedAt() { return createdAt; }
    public Long getFilledAt() { return filledAt; }
    public void setFilledAt(Long filledAt) { this.filledAt = filledAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public BigDecimal getReservedAmount() { return reservedAmount; }
    public void setReservedAmount(BigDecimal reservedAmount) { this.reservedAmount = reservedAmount; }
}
