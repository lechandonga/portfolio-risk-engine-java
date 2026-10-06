package com.github.lechandonga.portfolioriskenginejava.risk;

import java.math.BigDecimal;

/**
 * 风险指标（历史模拟法，按账户组合层面的历史损益分布计算）：
 * - historicalVaR：置信水平下的损失分位数（正数表示潜在损失金额）；
 * - status=IMPOSSIBLE 时各指标为 null，并给出可解释 reason（行情缺失/样本不足等），
 *   绝不用默认价或过期价凑出一个“看似正常”的数字。
 */
public record RiskMetrics(
        ValuationStatus status,
        BigDecimal confidence,
        int samples,
        BigDecimal historicalVaR,
        String reason) {

    public static RiskMetrics ok(BigDecimal confidence, int samples, BigDecimal var) {
        return new RiskMetrics(ValuationStatus.OK, confidence, samples, var, null);
    }

    public static RiskMetrics impossible(BigDecimal confidence, int samples, String reason) {
        return new RiskMetrics(ValuationStatus.IMPOSSIBLE, confidence, samples, null, reason);
    }
}
