package com.github.lechandonga.portfolioriskenginejava.trading;

import com.github.lechandonga.portfolioriskenginejava.domain.OrderType;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;

import java.math.BigDecimal;

public record OrderRequest(
        String accountId,
        String clientRequestId,
        String symbol,
        Side side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal limitPrice) {
}
