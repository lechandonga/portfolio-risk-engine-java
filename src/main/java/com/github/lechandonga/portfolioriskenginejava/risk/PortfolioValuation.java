package com.github.lechandonga.portfolioriskenginejava.risk;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioValuation(
        String accountId,
        BigDecimal cash,
        BigDecimal realizedPnl,
        BigDecimal totalCostBasis,
        BigDecimal totalMarketValue,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalEquity,
        ValuationStatus status,
        String reason,
        List<PositionValuation> positions,
        RiskMetrics risk) {
}
