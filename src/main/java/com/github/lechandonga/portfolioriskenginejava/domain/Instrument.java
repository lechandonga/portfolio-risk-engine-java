package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;

/** A tradable instrument. Lot size constrains order quantities (e.g. 100 for A-shares). */
public class Instrument {
    private final String symbol;
    private final String name;
    private final int lotSize;
    /** Orders priced more than this fraction away from the reference price are rejected. */
    private final BigDecimal maxPriceDeviation;
    private volatile boolean active;

    public Instrument(String symbol, String name, int lotSize, BigDecimal maxPriceDeviation, boolean active) {
        this.symbol = symbol;
        this.name = name;
        this.lotSize = lotSize;
        this.maxPriceDeviation = maxPriceDeviation;
        this.active = active;
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public int getLotSize() { return lotSize; }
    public BigDecimal getMaxPriceDeviation() { return maxPriceDeviation; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
