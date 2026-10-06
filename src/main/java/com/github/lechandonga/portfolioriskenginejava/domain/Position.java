package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;

/**
 * 持仓。quantity 为当前持有量；availableQty 为可卖数量（被未成交卖单占用时更小）。
 * costBasis 为剩余持仓的净成本（买入含费累计，卖出按移动平均冲减），
 * 用于计算未实现盈亏；已实现盈亏独立累计在账户上。
 */
public class Position {

    private final String symbol;
    private BigDecimal quantity;
    private BigDecimal availableQty;
    private BigDecimal costBasis;

    public Position(String symbol) {
        this.symbol = symbol;
        this.quantity = BigDecimal.ZERO;
        this.availableQty = BigDecimal.ZERO;
        this.costBasis = BigDecimal.ZERO;
    }

    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }
    public BigDecimal getCostBasis() { return costBasis; }
    public void setCostBasis(BigDecimal costBasis) { this.costBasis = costBasis; }
}
