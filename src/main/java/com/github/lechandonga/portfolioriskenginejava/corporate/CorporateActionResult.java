package com.github.lechandonga.portfolioriskenginejava.corporate;

import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;

import java.math.BigDecimal;

public record CorporateActionResult(
        String actionId,
        String accountId,
        String symbol,
        CorporateAction.Type type,
        BigDecimal quantityBefore,
        BigDecimal quantityAfter,
        BigDecimal costBasisBefore,
        BigDecimal costBasisAfter,
        BigDecimal cashPaid,
        boolean duplicated,
        String message) {
}
