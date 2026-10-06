package com.github.lechandonga.portfolioriskenginejava.api.dto;

import com.github.lechandonga.portfolioriskenginejava.domain.OrderType;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;

import java.math.BigDecimal;

public record OrderRequestDto(
        String clientRequestId,
        String symbol,
        Side side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal limitPrice) {
}
