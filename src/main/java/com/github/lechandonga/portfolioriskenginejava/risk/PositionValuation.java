package com.github.lechandonga.portfolioriskenginejava.risk;

import java.math.BigDecimal;

public record PositionValuation(
        String symbol,
        BigDecimal quantity,
        BigDecimal costBasis,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        ValuationStatus status,
        String reason) {

    public static PositionValuation impossible(String symbol, BigDecimal quantity,
                                               BigDecimal costBasis, String reason) {
        return new PositionValuation(symbol, quantity, costBasis, null, null, null,
                ValuationStatus.IMPOSSIBLE, reason);
    }
}
