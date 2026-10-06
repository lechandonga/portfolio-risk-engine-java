package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CorporateActionRepository {
    private final Map<String, CorporateAction> actions = new ConcurrentHashMap<>();
    /** actionId -> accounts that already absorbed it. Re-application is rejected. */
    private final Map<String, Set<String>> appliedByAccount = new ConcurrentHashMap<>();

    public CorporateAction save(CorporateAction action) {
        actions.put(action.getActionId(), action);
        return action;
    }

    public CorporateAction get(String actionId) {
        return actions.get(actionId);
    }

    /** Atomically claims application for an account. Returns false if already applied. */
    public boolean claim(String actionId, String accountId) {
        return appliedByAccount
                .computeIfAbsent(actionId, k -> ConcurrentHashMap.newKeySet())
                .add(accountId);
    }

    public boolean isApplied(String actionId, String accountId) {
        Set<String> set = appliedByAccount.get(actionId);
        return set != null && set.contains(accountId);
    }

    public Set<String> appliedAccounts(String actionId) {
        return new HashSet<>(appliedByAccount.getOrDefault(actionId, Set.of()));
    }
}
