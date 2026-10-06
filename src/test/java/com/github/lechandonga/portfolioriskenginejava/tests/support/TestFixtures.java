package com.github.lechandonga.portfolioriskenginejava.tests.support;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.Instrument;
import com.github.lechandonga.portfolioriskenginejava.market.LocalMarketDataSource;
import com.github.lechandonga.portfolioriskenginejava.market.Quote;
import com.github.lechandonga.portfolioriskenginejava.service.AccountRepository;
import com.github.lechandonga.portfolioriskenginejava.service.InstrumentRepository;
import com.github.lechandonga.portfolioriskenginejava.service.LedgerRepository;

import java.math.BigDecimal;
import java.util.function.LongSupplier;

public final class TestFixtures {
    private TestFixtures() {}

    public static Account account(AccountRepository accounts, LedgerRepository ledger, LongSupplier clock,
                                  String id, String cash) {
        Account account = new Account(id, id);
        account.setCash(new BigDecimal(cash));
        accounts.save(account);
        ledger.append(id, "DEPOSIT", "test-seed", new BigDecimal(cash), clock.getAsLong());
        return account;
    }

    public static Instrument instrument(InstrumentRepository repo, String symbol, int lot,
                                        boolean active) {
        Instrument instrument = new Instrument(symbol, symbol, lot, new BigDecimal("0.10"), active);
        repo.save(instrument);
        return instrument;
    }

    public static void freshQuote(LocalMarketDataSource market, LongSupplier clock,
                                  String symbol, String price) {

        market.publish(new Quote(symbol, new BigDecimal(price), clock.getAsLong()));
    }

    public static void oldQuote(LocalMarketDataSource market, LongSupplier clock,
                                String symbol, String price, long ageMillis) {

        market.publish(new Quote(symbol, new BigDecimal(price), clock.getAsLong() - ageMillis));
    }
}
