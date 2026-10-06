package com.github.lechandonga.portfolioriskenginejava.store;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ServiceException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 内存账户存储 + 分段锁：
 * - 每个 accountId 映射到固定数量分段中的一把锁，不同账户完全并行；
 * - 同一账户的下单/撤单/公司行为串行化，保证现金、持仓、费用守恒；
 * - 行情刷新不持账户锁，账户只读快照在锁内构建，避免脏读与死锁。
 */
public class AccountStore {

    private static final int STRIPES = 64;

    private final ConcurrentHashMap<String, AccountEntry> entries = new ConcurrentHashMap<>();
    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    public AccountStore() {
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public AccountEntry createAccount(String accountId, BigDecimal initialCash) {
        ReentrantLock lock = lockFor(accountId);
        lock.lock();
        try {
            AccountEntry existing = entries.get(accountId);
            if (existing != null) {
                return existing;
            }
            AccountEntry entry = new AccountEntry(new Account(accountId, initialCash));
            entries.put(accountId, entry);
            return entry;
        } finally {
            lock.unlock();
        }
    }

    public AccountEntry getEntry(String accountId) {
        AccountEntry entry = entries.get(accountId);
        if (entry == null) {
            throw new ServiceException(ErrorCode.ACCOUNT_NOT_FOUND, accountId);
        }
        return entry;
    }

    /** 在账户锁内执行有返回值操作。 */
    public <T> T withAccount(String accountId, Function<AccountEntry, T> action) {
        AccountEntry entry = getEntry(accountId);
        ReentrantLock lock = lockFor(accountId);
        lock.lock();
        try {
            return action.apply(entry);
        } finally {
            lock.unlock();
        }
    }

    /** 逐账户独立执行；某个账户异常不会影响其他账户（异常收集在 errorCollector 中）。 */
    public void forEachAccountIsolated(Consumer<AccountEntry> action,
                                       Consumer<AccountFailure> errorCollector) {
        List<AccountEntry> snapshot = new ArrayList<>(entries.values());
        for (AccountEntry entry : snapshot) {
            ReentrantLock lock = lockFor(entry.getAccount().getAccountId());
            lock.lock();
            try {
                action.accept(entry);
            } catch (RuntimeException e) {
                errorCollector.accept(new AccountFailure(
                        entry.getAccount().getAccountId(), e));
            } finally {
                lock.unlock();
            }
        }
    }

    public List<AccountEntry> entries() {
        return new ArrayList<>(entries.values());
    }

    public Map<String, Account> accounts() {
        Map<String, Account> result = new ConcurrentHashMap<>();
        entries.forEach((id, e) -> result.put(id, e.getAccount()));
        return result;
    }

    private ReentrantLock lockFor(String accountId) {
        int index = Math.floorMod(accountId.hashCode(), STRIPES);
        return locks[index];
    }

    public record AccountFailure(String accountId, RuntimeException cause) {
    }
}
