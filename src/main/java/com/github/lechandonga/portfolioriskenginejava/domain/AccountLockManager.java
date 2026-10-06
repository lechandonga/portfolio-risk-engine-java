package com.github.lechandonga.portfolioriskenginejava.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-account lock stripes. All operations mutating an account's cash/positions/orders
 * (including corporate actions and fills) hold the account lock for their whole
 * read-modify-write sequence, guaranteeing serialisable state per account while
 * different accounts execute concurrently.
 */
@Component
public class AccountLockManager {
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public Object lockFor(String accountId) {
        return locks.computeIfAbsent(accountId, k -> new Object());
    }
}
