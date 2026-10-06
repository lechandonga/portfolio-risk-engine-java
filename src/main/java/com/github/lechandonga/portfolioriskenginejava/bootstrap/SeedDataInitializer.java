package com.github.lechandonga.portfolioriskenginejava.bootstrap;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.Instrument;
import com.github.lechandonga.portfolioriskenginejava.market.LocalMarketDataSource;
import com.github.lechandonga.portfolioriskenginejava.service.AccountRepository;
import com.github.lechandonga.portfolioriskenginejava.service.InstrumentRepository;
import com.github.lechandonga.portfolioriskenginejava.service.LedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Deterministic local seed: fixed instruments, accounts, quotes and seeded price
 * histories so the service is usable out of the box and replays identically.
 */
@Configuration
public class SeedDataInitializer {
    private static final Logger log = LoggerFactory.getLogger(SeedDataInitializer.class);

    @Bean
    public ApplicationRunner seed(InstrumentRepository instruments, AccountRepository accounts,
                                  LedgerRepository ledger, LocalMarketDataSource market,
                                  LongSupplier clock) {
        return args -> {
            instruments.save(new Instrument("AAA", "Alpha Co", 100, new BigDecimal("0.10"), true));
            instruments.save(new Instrument("BBB", "Beta Inc", 100, new BigDecimal("0.10"), true));
            instruments.save(new Instrument("CCC", "Gamma Ltd", 1, new BigDecimal("0.20"), false));

            market.generateHistory("AAA", new BigDecimal("50.00"), 0.012, 250, 42);
            market.generateHistory("BBB", new BigDecimal("20.00"), 0.020, 250, 7);
            // Publish quotes on the engine clock (tests use a fixed controllable clock).
            market.publish(new com.github.lechandonga.portfolioriskenginejava.market.Quote(
                    "AAA", new BigDecimal("50.00"), clock.getAsLong()));
            market.publish(new com.github.lechandonga.portfolioriskenginejava.market.Quote(
                    "BBB", new BigDecimal("20.00"), clock.getAsLong()));

            seedAccount(accounts, ledger, market, clock, "acct-1", "Alice", new BigDecimal("500000"));
            seedAccount(accounts, ledger, market, clock, "acct-2", "Bob", new BigDecimal("200000"));
            log.info("seed data initialised: instruments=AAA,BBB (active), CCC (inactive); accounts=acct-1,acct-2");
        };
    }

    private void seedAccount(AccountRepository accounts, LedgerRepository ledger,
                             LocalMarketDataSource market, LongSupplier clock,
                             String id, String name, BigDecimal cash) {
        Account account = new Account(id, name);
        account.setCash(cash);
        accounts.save(account);
        ledger.append(id, "DEPOSIT", "seed", cash, clock.getAsLong());
    }
}
