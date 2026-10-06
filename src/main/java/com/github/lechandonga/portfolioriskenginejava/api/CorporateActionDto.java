package com.github.lechandonga.portfolioriskenginejava.api;

import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;

import java.math.BigDecimal;

public record CorporateActionDto(
        String actionId,
        String symbol,
        CorporateAction.Type type,
        BigDecimal factor,
        BigDecimal cashPerShare) {
}
