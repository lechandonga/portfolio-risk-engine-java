package com.github.lechandonga.portfolioriskenginejava.market;

/** 行情来源内部异常分类：超时 / 脏数据 / 其他不可用。 */
public class MarketDataException extends RuntimeException {

    public enum Kind { TIMEOUT, DIRTY, UNAVAILABLE }

    private final Kind kind;

    public MarketDataException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public MarketDataException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
