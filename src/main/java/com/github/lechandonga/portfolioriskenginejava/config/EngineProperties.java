package com.github.lechandonga.portfolioriskenginejava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "risk.engine")
public class EngineProperties {

    /** 手续费费率（成交额占比）。 */
    private BigDecimal feeRate = new BigDecimal("0.0003");
    /** 单笔最低手续费。 */
    private BigDecimal minFee = new BigDecimal("1.00");
    /** 默认 VaR 置信水平。 */
    private String confidence = "0.95";
    /** VaR 最少历史价格点数。 */
    private int minHistorySamples = 30;
    /** 报价允许的最大陈旧秒数，超过即视为无法计算。 */
    private long maxQuoteAgeSeconds = 3600;
    /** 演示账户初始现金。 */
    private BigDecimal demoInitialCash = new BigDecimal("1000000.00");
    /** 每个标的启动时生成的确定性种子历史样本数。 */
    private int seedHistorySize = 60;

    public BigDecimal getFeeRate() { return feeRate; }
    public void setFeeRate(BigDecimal feeRate) { this.feeRate = feeRate; }
    public BigDecimal getMinFee() { return minFee; }
    public void setMinFee(BigDecimal minFee) { this.minFee = minFee; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public int getMinHistorySamples() { return minHistorySamples; }
    public void setMinHistorySamples(int minHistorySamples) {
        this.minHistorySamples = minHistorySamples;
    }
    public long getMaxQuoteAgeSeconds() { return maxQuoteAgeSeconds; }
    public void setMaxQuoteAgeSeconds(long maxQuoteAgeSeconds) {
        this.maxQuoteAgeSeconds = maxQuoteAgeSeconds;
    }
    public BigDecimal getDemoInitialCash() { return demoInitialCash; }
    public void setDemoInitialCash(BigDecimal demoInitialCash) {
        this.demoInitialCash = demoInitialCash;
    }
    public int getSeedHistorySize() { return seedHistorySize; }
    public void setSeedHistorySize(int seedHistorySize) {
        this.seedHistorySize = seedHistorySize;
    }
}
