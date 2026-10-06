package com.github.lechandonga.portfolioriskenginejava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "engine")
public class EngineProperties {
    private final Fee fee = new Fee();
    private final Risk risk = new Risk();
    private final Market market = new Market();

    public static class Fee {
        /** Commission rate applied to notional (0.0003 = 3bp). */
        private BigDecimal commissionRate = new BigDecimal("0.0003");
        private BigDecimal minCommission = new BigDecimal("5.00");
        /** Per-side stamp-duty-like rate applied to sells only. */
        private BigDecimal sellDutyRate = new BigDecimal("0.001");

        public BigDecimal getCommissionRate() { return commissionRate; }
        public void setCommissionRate(BigDecimal v) { this.commissionRate = v; }
        public BigDecimal getMinCommission() { return minCommission; }
        public void setMinCommission(BigDecimal v) { this.minCommission = v; }
        public BigDecimal getSellDutyRate() { return sellDutyRate; }
        public void setSellDutyRate(BigDecimal v) { this.sellDutyRate = v; }
    }

    public static class Risk {
        /** Confidence level for VaR/ES, e.g. 0.95 or 0.99. */
        private BigDecimal confidence = new BigDecimal("0.95");
        private int historyPoints = 250;
        /** Minimum daily-return observations required before VaR/ES is produced. */
        private int minReturnPoints = 30;
        private BigDecimal minConfidence = new BigDecimal("0.50");
        private BigDecimal maxConfidence = new BigDecimal("0.9999");

        public BigDecimal getConfidence() { return confidence; }
        public void setConfidence(BigDecimal v) { this.confidence = v; }
        public int getHistoryPoints() { return historyPoints; }
        public void setHistoryPoints(int v) { this.historyPoints = v; }
        public int getMinReturnPoints() { return minReturnPoints; }
        public void setMinReturnPoints(int v) { this.minReturnPoints = v; }
        public BigDecimal getMinConfidence() { return minConfidence; }
        public void setMinConfidence(BigDecimal v) { this.minConfidence = v; }
        public BigDecimal getMaxConfidence() { return maxConfidence; }
        public void setMaxConfidence(BigDecimal v) { this.maxConfidence = v; }
    }

    public static class Market {
        private long stalenessMillis = 15_000;
        private long timeoutMillis = 500;

        public long getStalenessMillis() { return stalenessMillis; }
        public void setStalenessMillis(long v) { this.stalenessMillis = v; }
        public long getTimeoutMillis() { return timeoutMillis; }
        public void setTimeoutMillis(long v) { this.timeoutMillis = v; }
    }

    public Fee getFee() { return fee; }
    public Risk getRisk() { return risk; }
    public Market getMarket() { return market; }
}
