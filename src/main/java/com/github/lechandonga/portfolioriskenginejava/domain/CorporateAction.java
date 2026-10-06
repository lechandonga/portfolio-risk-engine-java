package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;

/**
 * 公司行为：
 * SPLIT：factor 为每 1 股拆成 factor 股（如 2.0 表示 1 拆 2），现金分红为 0；
 * DIVIDEND：factor 忽略，cashPerShare 为每股税前现金分红。
 * actionId 用于幂等去重。
 */
public class CorporateAction {

    public enum Type { SPLIT, DIVIDEND }

    private final String actionId;
    private final String symbol;
    private final Type type;
    private final BigDecimal factor;
    private final BigDecimal cashPerShare;

    public CorporateAction(String actionId, String symbol, Type type,
                           BigDecimal factor, BigDecimal cashPerShare) {
        this.actionId = actionId;
        this.symbol = symbol;
        this.type = type;
        this.factor = factor;
        this.cashPerShare = cashPerShare;
    }

    public String getActionId() { return actionId; }
    public String getSymbol() { return symbol; }
    public Type getType() { return type; }
    public BigDecimal getFactor() { return factor; }
    public BigDecimal getCashPerShare() { return cashPerShare; }
}
