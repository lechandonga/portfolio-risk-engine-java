package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;

/**
 * SPLIT: ratio new:old (e.g. 2.0 doubles shares, cost basis unchanged in total).
 * CASH_DIVIDEND: cashPerShare paid on settled quantity at the ex-date.
 */
public class CorporateAction {
    public enum Type { SPLIT, CASH_DIVIDEND }

    private final String actionId;
    private final String symbol;
    private final Type type;
    private final BigDecimal ratio;       // SPLIT only
    private final BigDecimal cashPerShare; // CASH_DIVIDEND only
    private final long exDate;

    public CorporateAction(String actionId, String symbol, Type type, BigDecimal ratio,
                           BigDecimal cashPerShare, long exDate) {
        this.actionId = actionId;
        this.symbol = symbol;
        this.type = type;
        this.ratio = ratio;
        this.cashPerShare = cashPerShare;
        this.exDate = exDate;
    }

    public String getActionId() { return actionId; }
    public String getSymbol() { return symbol; }
    public Type getType() { return type; }
    public BigDecimal getRatio() { return ratio; }
    public BigDecimal getCashPerShare() { return cashPerShare; }
    public long getExDate() { return exDate; }
}
