package com.github.lechandonga.portfolioriskenginejava.market;

import com.github.lechandonga.portfolioriskenginejava.money.Money;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 行情来源边界装饰器：
 * 1) 把任何底层异常（包括超时、脏数据、未检出异常）翻译为分类明确的
 *    {@link MarketDataException}，绝不把底层异常裸抛给上层；
 * 2) 校验价格：null、NaN、负价、零价都视为脏数据；
 * 3) 校验报价陈旧度：超过 maxAge 的报价明确标记为“过期”，上层不得静默使用。
 */
public class ValidatingMarketDataProvider implements MarketDataProvider {

    private final MarketDataProvider delegate;
    private final SimClock clock;
    private final Duration maxQuoteAge;

    public ValidatingMarketDataProvider(MarketDataProvider delegate, SimClock clock,
                                        Duration maxQuoteAge) {
        this.delegate = delegate;
        this.clock = clock;
        this.maxQuoteAge = maxQuoteAge;
    }

    @Override
    public Quote getQuote(String symbol) {
        try {
            Quote quote = delegate.getQuote(symbol);
            if (quote == null) {
                return null;
            }
            validatePrice(quote.lastPrice());
            return quote;
        } catch (MarketDataException e) {
            throw e;
        } catch (Exception e) {
            throw new MarketDataException(MarketDataException.Kind.UNAVAILABLE,
                    "行情来源调用失败", e);
        }
    }

    @Override
    public List<BigDecimal> getHistory(String symbol, int points) {
        try {
            List<BigDecimal> raw = delegate.getHistory(symbol, points);
            List<BigDecimal> clean = new ArrayList<>();
            if (raw != null) {
                for (BigDecimal p : raw) {
                    validatePrice(p);
                    clean.add(p);
                }
            }
            return clean;
        } catch (MarketDataException e) {
            throw e;
        } catch (Exception e) {
            throw new MarketDataException(MarketDataException.Kind.UNAVAILABLE,
                    "行情历史调用失败", e);
        }
    }

    @Override
    public void registerSymbol(String symbol, BigDecimal initialPrice,
                               List<BigDecimal> seedHistory) {
        delegate.registerSymbol(symbol, initialPrice, seedHistory);
    }

    @Override
    public List<String> symbols() {
        return delegate.symbols();
    }

    public boolean isFresh(Quote quote) {
        if (quote == null) {
            return false;
        }
        return Duration.between(quote.timestamp(), clock.now()).compareTo(maxQuoteAge) <= 0;
    }

    /** 取“最新且未过期且合法”的报价；null 表示无法计算（区别于价格 0）。 */
    public Quote freshQuote(String symbol) {
        Quote quote = getQuote(symbol);
        if (quote == null) {
            return null;
        }
        if (!isFresh(quote)) {
            return null;
        }
        return quote;
    }

    public Instant clockNow() {
        return clock.now();
    }

    private void validatePrice(BigDecimal price) {
        double d = price == null ? Double.NaN : price.doubleValue();
        if (price == null || price.signum() <= 0 || Double.isNaN(d) || Double.isInfinite(d)) {
            throw new MarketDataException(MarketDataException.Kind.DIRTY, "非法价格");
        }
        Money.money(price);
    }
}
