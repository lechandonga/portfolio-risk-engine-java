package com.github.lechandonga.portfolioriskenginejava.market;

import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ServiceException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 行情服务：推进模拟随机游走，或按给定序列做确定性回放。
 * 回放要求时间戳严格递增且标的存在，否则给出独立错误码；
 * 相同操作序列（注册信息 + 回放/刷新序列）得到完全相同的行情与估值结果。
 */
public class MarketReplayService {

    private final SimulatedMarketDataProvider provider;

    public MarketReplayService(SimulatedMarketDataProvider provider) {
        this.provider = provider;
    }

    public List<Quote> refresh() {
        try {
            return provider.refresh();
        } catch (MarketDataException e) {
            throw toService(e);
        }
    }

    public List<Quote> replay(List<ReplayTick> ticks) {
        List<Quote> published = new ArrayList<>();
        for (ReplayTick tick : ticks) {
            if (!provider.symbols().contains(tick.symbol())) {
                throw new ServiceException(ErrorCode.REPLAY_UNKNOWN_SYMBOL, tick.symbol());
            }
            try {
                published.add(provider.publish(
                        tick.symbol(), tick.price(), tick.timestamp()));
            } catch (IllegalStateException e) {
                throw new ServiceException(ErrorCode.REPLAY_BAD_TIMESTAMP,
                        tick.symbol() + " @ " + tick.timestamp());
            } catch (MarketDataException e) {
                throw toService(e);
            }
        }
        return published;
    }

    public void injectFailure(MarketDataException.Kind kind) {
        provider.injectFailure(kind);
    }

    public void clearFailure() {
        provider.clearInjectedFailure();
    }

    private ServiceException toService(MarketDataException e) {
        return new ServiceException(switch (e.getKind()) {
            case TIMEOUT -> ErrorCode.MARKET_DATA_TIMEOUT;
            case DIRTY -> ErrorCode.MARKET_DATA_DIRTY;
            case UNAVAILABLE -> ErrorCode.MARKET_DATA_UNAVAILABLE;
        }, e.getMessage());
    }

    public record ReplayTick(String symbol, BigDecimal price, Instant timestamp) {
    }
}
