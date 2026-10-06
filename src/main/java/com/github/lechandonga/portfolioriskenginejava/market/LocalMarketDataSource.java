package com.github.lechandonga.portfolioriskenginejava.market;

import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fully local, deterministic-or-randomised simulated source. No network access.
 * Supports fault injection per symbol so tests can prove the engine distinguishes
 * timeout / dirty data / missing quote and never leaks the underlying exception.
 */
public class LocalMarketDataSource implements MarketDataSource {
    public enum Fault { NONE, TIMEOUT, DIRTY, MISSING }

    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<String, List<BigDecimal>> histories = new ConcurrentHashMap<>();
    /**
     * One-shot fault injection: the next {@code latest}/{@code history} call observes the
     * injected fault exactly once, then state auto-resets. Works across the engine's
     * timeout-worker thread and can never leak between tests.
     */
    private final java.util.concurrent.atomic.AtomicReference<Fault> oneShotFault =
            new java.util.concurrent.atomic.AtomicReference<>(Fault.NONE);
    /** Persistent fault for tests that want every call to fail until they clear it. */
    private final java.util.concurrent.atomic.AtomicReference<Fault> stickyFault =
            new java.util.concurrent.atomic.AtomicReference<>(Fault.NONE);
    private final long timeoutMillis;

    public LocalMarketDataSource(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public void publish(Quote quote) {
        quotes.put(quote.symbol(), quote);
    }

    public void publishHistory(String symbol, List<BigDecimal> closes) {
        histories.put(symbol, new ArrayList<>(closes));
    }

    /** Generate a deterministic geometric-random-walk history (fixed seed => replayable). */
    public void generateHistory(String symbol, BigDecimal start, double dailyVol, int points, long seed) {
        Random rnd = new Random(seed);
        List<BigDecimal> series = new ArrayList<>(points);
        double price = start.doubleValue();
        for (int i = 0; i < points; i++) {
            double shock = rnd.nextGaussian() * dailyVol;
            price = Math.max(0.01, price * (1.0 + shock));
            series.add(BigDecimal.valueOf(price).setScale(4, java.math.RoundingMode.HALF_UP));
        }
        histories.put(symbol, series);
        quotes.put(symbol, new Quote(symbol, series.get(series.size() - 1), System.currentTimeMillis()));
    }

    /** Inject a fault observed by the next call only. */
    public void setFault(Fault fault) {
        oneShotFault.set(fault);
    }

    /** Inject a fault that stays until {@link #clearFault()}. */
    public void setStickyFault(Fault fault) {
        stickyFault.set(fault);
    }

    public void clearFault() {
        oneShotFault.set(Fault.NONE);
        stickyFault.set(Fault.NONE);
    }

    private Fault currentFault() {
        Fault sticky = stickyFault.get();
        if (sticky != Fault.NONE) {
            return sticky;
        }
        return oneShotFault.getAndSet(Fault.NONE);
    }

    @Override
    public Quote latest(String symbol) {
        switch (currentFault()) {
            case TIMEOUT -> {
                try {
                    Thread.sleep(timeoutMillis + 50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new MarketDataSourceException(ErrorCode.MARKET_DATA_TIMEOUT,
                        "quote source timed out for " + symbol);
            }
            case DIRTY -> throw new MarketDataSourceException(ErrorCode.MARKET_DATA_DIRTY,
                    "source returned an unparseable quote for " + symbol);
            case MISSING -> {
                return null;
            }
            default -> {
                return quotes.get(symbol);
            }
        }
    }

    @Override
    public List<BigDecimal> history(String symbol, int maxPoints) {
        if (currentFault() == Fault.DIRTY) {
            throw new MarketDataSourceException(ErrorCode.MARKET_DATA_DIRTY,
                    "source returned corrupt history for " + symbol);
        }
        List<BigDecimal> full = histories.get(symbol);
        if (full == null) {
            return null;
        }
        if (full.size() <= maxPoints) {
            return List.copyOf(full);
        }
        return List.copyOf(full.subList(full.size() - maxPoints, full.size()));
    }
}
