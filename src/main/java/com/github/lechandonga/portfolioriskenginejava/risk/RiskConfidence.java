package com.github.lechandonga.portfolioriskenginejava.risk;

import java.math.BigDecimal;

/**
 * 风险参数。confidence 必须在 (0,1) 开区间（如 0.95）；
 * minHistorySamples 为可计算 VaR 所需的最少价格点数（含当前点，默认 30）。
 */
public record RiskConfidence(BigDecimal confidence, int minHistorySamples) {

    public static RiskConfidence of(String confidence, int minHistorySamples) {
        return new RiskConfidence(new BigDecimal(confidence), minHistorySamples);
    }
}
