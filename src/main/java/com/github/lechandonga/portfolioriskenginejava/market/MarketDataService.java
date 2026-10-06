package com.github.lechandonga.portfolioriskenginejava.market;

import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Engine-facing facade over a pluggable {@link MarketDataSource}.
 * Responsibilities:
 *  - timeout enforcement independent of the adapter
 *  - catch & normalise every adapter failure into MARKET_* BusinessException
 *  - freshness validation against the engine clock (no stale quote used silently)
 *  - dirty-data validation (null/non-positive prices)
 */
public class MarketDataService {
    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final MarketDataSource source;
    private final LongSupplier clock;
    private final long stalenessMillis;
    private final long timeoutMillis;

    public MarketDataService(MarketDataSource source, LongSupplier clock, long stalenessMillis, long timeoutMillis) {
        this.source = source;
        this.clock = clock;
        this.stalenessMillis = stalenessMillis;
        this.timeoutMillis = timeoutMillis;
    }

    /** Fresh, positive quote or a typed MARKET_* error. Never returns a stale/default price. */
    public Quote requireFreshQuote(String symbol) {
        Quote quote = callTimed(() -> source.latest(symbol), "quote");
        if (quote == null) {
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE,
                    "no quote available for " + symbol);
        }
        validatePrice(symbol, quote.price());
        if (clock.getAsLong() - quote.asOf() > stalenessMillis) {
            throw new BusinessException(ErrorCode.MARKET_DATA_STALE,
                    "quote for " + symbol + " is stale (asOf=" + quote.asOf() + ")");
        }
        return quote;
    }

    /** Returns null when unavailable rather than throwing; caller distinguishes unavailable vs zero. */
    public Quote tryFreshQuote(String symbol) {
        try {
            return requireFreshQuote(symbol);
        } catch (BusinessException ex) {
            log.debug("quote unavailable for {}: {}", symbol, ex.getCode());
            return null;
        }
    }

    public List<BigDecimal> requireHistory(String symbol, int maxPoints) {
        List<BigDecimal> history = callTimed(() -> source.history(symbol, maxPoints), "history");
        if (history == null) {
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE,
                    "no price history available for " + symbol);
        }
        for (BigDecimal price : history) {
            validatePrice(symbol, price);
        }
        return history;
    }

    private void validatePrice(String symbol, BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new BusinessException(ErrorCode.MARKET_DATA_DIRTY,
                    "non-positive or missing price for " + symbol);
        }
    }

    private <T> T callTimed(java.util.function.Supplier<T> call, String what) {
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "market-data-" + what);
                    t.setDaemon(true);
                    return t;
                });
        try {
            java.util.concurrent.Future<T> future = executor.submit(call::get);
            try {
                return future.get(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                throw new BusinessException(ErrorCode.MARKET_DATA_TIMEOUT,
                        what + " source did not respond within " + timeoutMillis + "ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                throw new BusinessException(ErrorCode.MARKET_DATA_TIMEOUT, what + " wait interrupted");
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof MarketDataSourceException mds) {
                    throw new BusinessException(mds.getCode(), mds.getMessage());
                }
                throw new BusinessException(ErrorCode.MARKET_SOURCE_FAULT, what + " source failed");
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
