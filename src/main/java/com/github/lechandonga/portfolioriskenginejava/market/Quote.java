package com.github.lechandonga.portfolioriskenginejava.market;

import java.math.BigDecimal;
import java.time.Instant;

/** 最新报价：价格与报价时间（用于陈旧性判定）。 */
public record Quote(String symbol, BigDecimal lastPrice, Instant timestamp) {
}
