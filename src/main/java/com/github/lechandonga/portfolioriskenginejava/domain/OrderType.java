package com.github.lechandonga.portfolioriskenginejava.domain;

public enum OrderType {
    /** 立即按最新行情成交；无行情则拒绝。 */
    MARKET,
    /** 限价单：买入价 <= limitPrice、卖出价 >= limitPrice 时立即成交，否则保留为 NEW。 */
    LIMIT
}
