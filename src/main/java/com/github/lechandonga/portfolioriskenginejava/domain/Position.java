package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Long-only position with weighted-average-cost bookkeeping, exact to the cent.
 * quantity        : settled holdings
 * reservedQuantity: shares blocked by NEW sell orders
 * costBasis       : total acquisition cost of the remaining quantity
 *
 * On a partial sale the proportional cost is rounded to cents and the sub-cent
 * residual is folded back into the remaining basis, so:
 *   bookedCost + remainingBasis == basisBefore
 * holds exactly for every trade.
 */
public class Position {
    private final String accountId;
    private final String symbol;
    private long quantity;
    private long reservedQuantity;
    private BigDecimal costBasis = Money.zero();

    public Position(String accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
    }

    public String getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public long getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(long reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public BigDecimal getCostBasis() { return costBasis; }
    public void setCostBasis(BigDecimal costBasis) { this.costBasis = Money.of(costBasis); }

    public long availableQuantity() {
        return quantity - reservedQuantity;
    }

    public BigDecimal averageCost() {
        return quantity == 0 ? Money.zero()
                : costBasis.divide(BigDecimal.valueOf(quantity), 6, RoundingMode.HALF_UP);
    }

    public BigDecimal removeCost(long qty) {
        if (quantity == 0 || qty == 0) {
            return Money.zero();
        }
        long remainingQty = quantity - qty;
        if (remainingQty == 0) {
            BigDecimal booked = costBasis;
            costBasis = Money.zero();
            return booked;
        }
        BigDecimal proportional = costBasis
                .multiply(BigDecimal.valueOf(remainingQty))
                .divide(BigDecimal.valueOf(quantity), 6, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal booked = costBasis.subtract(proportional);
        costBasis = proportional;
        return booked;
    }
}
