package com.github.lechandonga.portfolioriskenginejava.dto;

import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;
import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderStatus;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;

import java.math.BigDecimal;
import java.util.List;

public final class Responses {
    private Responses() {}

    public record AccountView(String accountId, String name, BigDecimal cash,
                              BigDecimal reservedCash, BigDecimal availableCash) {}

    public record OrderView(String orderId, String clientRequestId, String accountId, String symbol,
                            Side side, long quantity, BigDecimal limitPrice, OrderStatus status,
                            BigDecimal filledPrice, BigDecimal fee, String rejectReason,
                            long createdAt, Long filledAt) {
        public static OrderView from(Order o) {
            return new OrderView(o.getOrderId(), o.getClientRequestId(), o.getAccountId(), o.getSymbol(),
                    o.getSide(), o.getQuantity(), o.getLimitPrice(), o.getStatus(), o.getFilledPrice(),
                    o.getFee(), o.getRejectReason(), o.getCreatedAt(), o.getFilledAt());
        }
    }

    /** OK / UNAVAILABLE distinguishes "legitimately zero" from "cannot be computed". */
    public enum ValuationStatus { OK, MARKET_DATA_UNAVAILABLE }

    public record PositionValuation(String symbol, long quantity, long availableQuantity,
                                    BigDecimal averageCost, BigDecimal costBasis,
                                    BigDecimal lastPrice, BigDecimal marketValue,
                                    BigDecimal unrealizedPnl, ValuationStatus status, String reason) {}

    public record PortfolioValuation(String accountId, BigDecimal cash, BigDecimal reservedCash,
                                     BigDecimal totalMarketValue, BigDecimal totalUnrealizedPnl,
                                     BigDecimal realizedPnl, BigDecimal totalEquity,
                                     List<PositionValuation> positions,
                                     List<String> unavailableSymbols) {}

    public enum RiskStatus { OK, NOT_COMPUTABLE }

    public record PositionRisk(String symbol, BigDecimal marketValue, BigDecimal weight,
                               BigDecimal dailyVolatility, BigDecimal var,
                               BigDecimal expectedShortfall, RiskStatus status, String reason) {}

    public record RiskReport(String accountId, BigDecimal confidence, BigDecimal totalMarketValue,
                             BigDecimal portfolioVar, BigDecimal portfolioExpectedShortfall,
                             List<PositionRisk> positions, List<String> notComputableSymbols) {}

    public record CorporateActionView(String actionId, String symbol, CorporateAction.Type type,
                                      BigDecimal ratio, BigDecimal cashPerShare, long exDate,
                                      boolean alreadyApplied) {}
}
