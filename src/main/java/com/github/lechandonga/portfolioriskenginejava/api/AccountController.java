package com.github.lechandonga.portfolioriskenginejava.api;

import com.github.lechandonga.portfolioriskenginejava.money.Money;
import com.github.lechandonga.portfolioriskenginejava.store.AccountStore;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountStore store;

    public AccountController(AccountStore store) {
        this.store = store;
    }

    @PostMapping
    public Map<String, Object> create(@RequestParam String accountId,
                                      @RequestParam BigDecimal initialCash) {
        var entry = store.createAccount(accountId, Money.money(initialCash));
        return Map.of(
                "accountId", entry.getAccount().getAccountId(),
                "cash", entry.getAccount().getCash());
    }
}
