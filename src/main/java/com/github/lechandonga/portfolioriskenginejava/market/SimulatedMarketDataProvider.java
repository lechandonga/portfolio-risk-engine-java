package com.github.lechandonga.portfolioriskenginejava.market;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地确定性行情源：
 * - 所有标的初始价格与历史样本由启动配置给出；
 * - {@link #refresh()} 用固定种子的线性同余随机游走生成下一根价格，无任何外部依赖；
 * - {@link #publish} 支持行情回放（Replay），时间戳必须递增，结果可精确复现；
 * - 可注入故障（超时/脏数据/不可用）以验证错误隔离与翻译逻辑。
 */
public class SimulatedMarketDataProvider implements MarketDataProvider {

    private static final int HISTORY_LIMIT = 512;
    private static final MathContext MC = MathContext.DECIMAL64;

    private record Book(Deque<BigDecimal> history, Instant lastTime) {
    }

    private final Map<String, Book> books = new ConcurrentHashMap<>();
    private final SimClock clock;
    private final AtomicLong walkStep = new AtomicLong();
    private volatile MarketDataException.Kind injectedFailure;

    public SimulatedMarketDataProvider(SimClock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized void registerSymbol(String symbol, BigDecimal initialPrice,
                                            List<BigDecimal> seedHistory) {
        Deque<BigDecimal> hist = new ArrayDeque<>();
        if (seedHistory != null) {
            seedHistory.forEach(hist::addLast);
        }
        if (hist.isEmpty()) {
            hist.add(initialPrice);
        }
        books.put(symbol, new Book(hist, clock.now()));
    }

    @Override
    public synchronized Quote getQuote(String symbol) {
        failIfInjected();
        Book book = books.get(symbol);
        if (book == null) {
            return null;
        }
        BigDecimal price = book.history.peekLast();
        if (injectedFailure == MarketDataException.Kind.DIRTY) {
            price = BigDecimal.valueOf(-1);
        }
        return new Quote(symbol, price, book.lastTime);
    }

    @Override
    public synchronized List<BigDecimal> getHistory(String symbol, int points) {
        failIfInjected();
        Book book = books.get(symbol);
        if (book == null) {
            return List.of();
        }
        List<BigDecimal> all = new ArrayList<>(book.history);
        int from = Math.max(0, all.size() - points);
        return new ArrayList<>(all.subList(from, all.size()));
    }

    @Override
    public List<String> symbols() {
        return new ArrayList<>(books.keySet());
    }

    /** 确定性随机游走推进全部标的的最新价。 */
    public synchronized List<Quote> refresh() {
        failIfInjected();
        clock.advanceSeconds(60);
        List<Quote> result = new ArrayList<>();
        long step = walkStep.incrementAndGet();
        for (Map.Entry<String, Book> e : books.entrySet()) {
            BigDecimal last = e.getValue().history.peekLast();
            double shock = nextShock(step, e.getKey());
            BigDecimal next = last.multiply(BigDecimal.valueOf(1.0d + shock), MC)
                    .max(BigDecimal.valueOf(0.01d));
            appendPrice(e.getKey(), e.getValue(), next, clock.now());
            result.add(new Quote(e.getKey(), next, clock.now()));
        }
        return result;
    }

    /** 行情回放：发布指定价格。时间戳必须严格晚于该标的上一条报价。 */
    public synchronized Quote publish(String symbol, BigDecimal price, Instant timestamp) {
        failIfInjected();
        Book book = books.get(symbol);
        if (book == null) {
            throw new MarketDataException(MarketDataException.Kind.UNAVAILABLE, "未知标的 " + symbol);
        }
        if (!timestamp.isAfter(book.lastTime)) {
            throw new IllegalStateException("timestamp-not-increasing");
        }
        clock.advanceTo(timestamp);
        appendPrice(symbol, book, price, timestamp);
        return new Quote(symbol, book.history.peekLast(), timestamp);
    }

    public synchronized void reset() {
        books.clear();
        walkStep.set(0);
        injectedFailure = null;
    }

    /** 测试用：注入行情源故障。 */
    public void injectFailure(MarketDataException.Kind kind) {
        this.injectedFailure = kind;
    }

    public void clearInjectedFailure() {
        this.injectedFailure = null;
    }

    private void appendPrice(String symbol, Book old, BigDecimal price, Instant time) {
        Deque<BigDecimal> hist = new ArrayDeque<>(old.history);
        hist.addLast(price);
        while (hist.size() > HISTORY_LIMIT) {
            hist.pollFirst();
        }
        books.put(symbol, new Book(hist, time));
    }

    private void failIfInjected() {
        MarketDataException.Kind kind = injectedFailure;
        if (kind == null) {
            return;
        }
        if (kind == MarketDataException.Kind.TIMEOUT) {
            throw new MarketDataException(MarketDataException.Kind.TIMEOUT, "模拟行情源超时");
        }
        if (kind == MarketDataException.Kind.UNAVAILABLE) {
            throw new MarketDataException(MarketDataException.Kind.UNAVAILABLE, "模拟行情源不可用");
        }
    }

    private double nextShock(long step, String symbol) {
        long seed = step * 1_000_003L + symbol.hashCode();
        long x = (seed * 1103515245L + 12345L) & 0x7fffffffL;
        return (x % 2001 - 1000) / 100_000.0d;
    }
}
