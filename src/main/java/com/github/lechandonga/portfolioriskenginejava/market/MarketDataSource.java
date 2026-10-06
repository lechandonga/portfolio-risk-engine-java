package com.github.lechandonga.portfolioriskenginejava.market;

import java.util.List;

/**
 * Replaceable market-data SPI. Implementations may be local simulators, file replay
 * loaders, or remote adapters. Implementations must throw MarketDataSourceException
 * (never raw transport exceptions) on timeout / dirty data.
 */
public interface MarketDataSource {
    /** @return latest quote, or null if the source has no quote for the symbol. */
    Quote latest(String symbol);

    /**
     * Historical daily (or per-tick) closing prices, oldest first, used for risk models.
     * Returns null when the source cannot provide history for the symbol.
     */
    List<java.math.BigDecimal> history(String symbol, int maxPoints);
}
