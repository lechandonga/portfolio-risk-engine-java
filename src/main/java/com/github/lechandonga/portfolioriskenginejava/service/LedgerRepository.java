package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.domain.LedgerEntry;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class LedgerRepository {
    private final CopyOnWriteArrayList<LedgerEntry> entries = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    public LedgerEntry append(String accountId, String type, String refId,
                              java.math.BigDecimal amount, long timestamp) {
        LedgerEntry entry = new LedgerEntry(sequence.incrementAndGet(), accountId, type, refId,
                com.github.lechandonga.portfolioriskenginejava.domain.Money.of(amount), timestamp);
        entries.add(entry);
        return entry;
    }

    public List<LedgerEntry> findByAccount(String accountId) {
        return entries.stream().filter(e -> e.accountId().equals(accountId)).toList();
    }
}
