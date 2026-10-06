package com.github.lechandonga.portfolioriskenginejava.market;

import java.util.List;

/**
 * 行情来源 SPI。生产可接入真实供应商（仍需在边界把异常翻译为
 * {@link MarketDataException}），本工程默认使用本地确定性模拟实现。
 */
public interface MarketDataProvider {

    /** 返回最新报价；标的未知或无数据时返回 null（语义为“无法计算”，区别于价格为 0）。 */
    Quote getQuote(String symbol);

    /** 按时间从旧到新返回最多 points 个历史收盘价；样本不足时返回更少的元素。 */
    List<java.math.BigDecimal> getHistory(String symbol, int points);

    /** 注册一个可交易标的并给定初始历史样本。 */
    void registerSymbol(String symbol, java.math.BigDecimal initialPrice,
                        List<java.math.BigDecimal> seedHistory);

    java.util.List<String> symbols();
}
