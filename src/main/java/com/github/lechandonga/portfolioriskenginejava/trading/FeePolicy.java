package com.github.lechandonga.portfolioriskenginejava.trading;

import com.github.lechandonga.portfolioriskenginejava.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 简化费用模型：fee = max(成交额 * feeRate, minFee)。
 * 买入费用计入持仓成本；卖出费用冲减已实现盈亏。
 */
public class FeePolicy {

    private final BigDecimal feeRate;
    private final BigDecimal minFee;

    public FeePolicy(BigDecimal feeRate, BigDecimal minFee) {
        this.feeRate = feeRate;
        this.minFee = minFee;
    }

    public BigDecimal fee(BigDecimal notional) {
        BigDecimal calculated = notional.multiply(feeRate)
                .setScale(Money.MONEY_SCALE, RoundingMode.HALF_UP);
        return calculated.max(minFee);
    }
}
