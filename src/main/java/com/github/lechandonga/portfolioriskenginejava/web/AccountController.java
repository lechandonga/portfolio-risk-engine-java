package com.github.lechandonga.portfolioriskenginejava.web;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.LedgerEntry;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.CreateAccountRequest;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.AccountView;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.service.AccountRepository;
import com.github.lechandonga.portfolioriskenginejava.service.LedgerRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.LongSupplier;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final LongSupplier clock;

    public AccountController(AccountRepository accounts, LedgerRepository ledger, LongSupplier clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(@Valid @RequestBody CreateAccountRequest request) {
        if (accounts.get(request.accountId()) != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "account already exists: " + request.accountId());
        }
        Account account = new Account(request.accountId(),
                request.name() != null ? request.name() : request.accountId());
        account.setCash(request.initialCash());
        accounts.save(account);
        ledger.append(request.accountId(), "DEPOSIT", "init", request.initialCash(), clock.getAsLong());
        return view(account);
    }

    @GetMapping("/{accountId}")
    public AccountView get(@PathVariable String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + accountId);
        }
        return view(account);
    }

    @GetMapping("/{accountId}/ledger")
    public List<LedgerEntry> ledger(@PathVariable String accountId) {
        if (accounts.get(accountId) == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + accountId);
        }
        return ledger.findByAccount(accountId);
    }

    private AccountView view(Account account) {
        return new AccountView(account.getAccountId(), account.getName(), account.getCash(),
                account.getReservedCash(), account.availableCash());
    }
}
