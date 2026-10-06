package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class AccountRepository {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public Account get(String accountId) {
        return accounts.get(accountId);
    }

    public Account save(Account account) {
        accounts.put(account.getAccountId(), account);
        return account;
    }

    public Collection<Account> findAll() {
        return accounts.values();
    }
}
