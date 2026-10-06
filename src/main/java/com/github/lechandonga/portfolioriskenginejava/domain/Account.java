package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** 账户：现金、可用现金（买单预留会减少）、持仓与累计已实现盈亏（含费用影响）。 */
public class Account {

    private final String accountId;
    private BigDecimal cash;
    private BigDecimal availableCash;
    private BigDecimal realizedPnl;
    private final Map<String, Position> positions = new HashMap<>();

    public Account(String accountId, BigDecimal initialCash) {
        this.accountId = accountId;
        this.cash = initialCash;
        this.availableCash = initialCash;
        this.realizedPnl = BigDecimal.ZERO;
    }

    public String getAccountId() { return accountId; }
    public BigDecimal getCash() { return cash; }
    public void setCash(BigDecimal cash) { this.cash = cash; }
    public BigDecimal getAvailableCash() { return availableCash; }
    public void setAvailableCash(BigDecimal availableCash) { this.availableCash = availableCash; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public Map<String, Position> getPositions() { return positions; }
}
