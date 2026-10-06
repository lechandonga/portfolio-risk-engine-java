package com.github.lechandonga.portfolioriskenginejava.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额与数量的统一舍入口径：所有现金/费用/市值使用 2 位小数 HALF_UP，
 * 数量支持 8 位小数（便于拆股比例调整）。
 */
public final class Money {

    public static final int MONEY_SCALE = 2;
    public static final int QTY_SCALE = 8;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Money() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING)
                : value.setScale(MONEY_SCALE, ROUNDING);
    }

    public static BigDecimal qty(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(QTY_SCALE, ROUNDING)
                : value.setScale(QTY_SCALE, ROUNDING);
    }

    public static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
