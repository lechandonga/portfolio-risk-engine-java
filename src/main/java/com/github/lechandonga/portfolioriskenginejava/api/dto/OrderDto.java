package com.github.lechandonga.portfolioriskenginejava.api.dto;

import com.github.lechandonga.portfolioriskenginejava.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderDto(
        long id,
        String clientRequestId,
        String symbol,
        String side,
        String type,
        BigDecimal quantity,
        BigDecimal limitPrice,
        String status,
        BigDecimal avgFillPrice,
        BigDecimal fee,
        String rejectReason,
        Instant createdAt,
        Instant filledAt) {

    public static OrderDto from(Order o) {
        return new OrderDto(o.getId(), o.getClientRequestId(), o.getSymbol(),
                o.getSide().name(), o.getType().name(), o.getQuantity(), o.getLimitPrice(),
                o.getStatus().name(), o.getAvgFillPrice(), o.getFee(),
                o.getRejectReason(), o.getCreatedAt(), o.getFilledAt());
    }
}
