package com.github.lechandonga.portfolioriskenginejava.store;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.Order;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 单账户聚合：账户本体 + 该账户的指令幂等索引 + 已应用公司行为集合。
 * 所有字段只允许在该账户的分段锁保护下变更。
 */
public class AccountEntry {

    private final Account account;
    private final Map<String, Long> orderIdsByClientRequest = new HashMap<>();
    private final Map<Long, Order> orders = new HashMap<>();
    private final Set<String> appliedCorporateActions = new HashSet<>();

    public AccountEntry(Account account) {
        this.account = account;
    }

    public Account getAccount() { return account; }
    public Map<String, Long> getOrderIdsByClientRequest() { return orderIdsByClientRequest; }
    public Map<Long, Order> getOrders() { return orders; }
    public Set<String> getAppliedCorporateActions() { return appliedCorporateActions; }
}
