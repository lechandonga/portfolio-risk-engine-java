package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;

public class Account {
    private final String accountId;
    private String name;
    /** Total settled cash + cash reserved by NEW buy orders. */
    private BigDecimal cash = BigDecimal.ZERO;
    /** Cash blocked by unfilled buy orders; available = cash - reservedCash. */
    private BigDecimal reservedCash = BigDecimal.ZERO;
    /** Cumulative realized PnL from sells and corporate-action cash flows are booked to cash. */
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    public Account(String accountId, String name) {
        this.accountId = accountId;
        this.name = name;
    }

    public String getAccountId() { return accountId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getCash() { return cash; }
    public void setCash(BigDecimal cash) { this.cash = Money.of(cash); }
    public BigDecimal getReservedCash() { return reservedCash; }
    public void setReservedCash(BigDecimal reservedCash) { this.reservedCash = Money.of(reservedCash); }

    public BigDecimal availableCash() {
        return cash.subtract(reservedCash);
    }

    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = Money.of(realizedPnl); }
}
