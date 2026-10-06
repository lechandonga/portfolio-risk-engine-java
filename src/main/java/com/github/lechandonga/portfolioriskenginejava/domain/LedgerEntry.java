package com.github.lechandonga.portfolioriskenginejava.domain;

import java.math.BigDecimal;

/** Immutable cash-ledger movement. The sum of signed amounts per account always reconciles to cash. */
public record LedgerEntry(long id, String accountId, String type, String refId,
                         BigDecimal amount, long timestamp) {
}
