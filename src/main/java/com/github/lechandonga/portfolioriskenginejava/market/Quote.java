package com.github.lechandonga.portfolioriskenginejava.market;

import java.math.BigDecimal;

/**
 * A single latest quote. {@code asOf} is source-clock epoch millis; consumers decide
 * staleness against the engine clock — a quote is never silently treated as fresh.
 */
public record Quote(String symbol, BigDecimal price, long asOf) {
}
