package com.github.lechandonga.portfolioriskenginejava.error;

/**
 * Stable, machine-readable error codes. Every rejected request carries one of these
 * plus a human-readable message, so callers can distinguish failure reasons instead
 * of matching on text.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    UNKNOWN_INSTRUMENT,
    INACTIVE_INSTRUMENT,
    ACCOUNT_NOT_FOUND,
    INSUFFICIENT_CASH,
    INSUFFICIENT_QUANTITY,
    ORDER_NOT_FOUND,
    ORDER_ALREADY_FINAL,
    ORDER_NOT_CANCELLABLE,
    ORDER_NOT_FILLABLE,
    QUANTITY_NOT_MULTIPLE_OF_LOT,
    PRICE_OUT_OF_BAND,
    MARKET_DATA_UNAVAILABLE,
    MARKET_DATA_STALE,
    MARKET_DATA_DIRTY,
    MARKET_DATA_TIMEOUT,
    MARKET_SOURCE_FAULT,
    RISK_NOT_COMPUTABLE,
    CORPORATE_ACTION_UNKNOWN_INSTRUMENT,
    CORPORATE_ACTION_DUPLICATE,
    CONCURRENT_MODIFICATION
}
