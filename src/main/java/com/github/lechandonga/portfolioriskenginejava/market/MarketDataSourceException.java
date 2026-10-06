package com.github.lechandonga.portfolioriskenginejava.market;

import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;

/** Internal adapter-level failure; translated to a controlled error before reaching callers. */
public class MarketDataSourceException extends RuntimeException {
    private final ErrorCode code;

    public MarketDataSourceException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
