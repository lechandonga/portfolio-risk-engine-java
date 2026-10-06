package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** All cash amounts normalise to 2 decimal places HALF_UP so ledger sums are exact. */
public final class Money {
    private Money() {}

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
