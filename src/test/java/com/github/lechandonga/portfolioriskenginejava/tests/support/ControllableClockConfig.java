package com.github.lechandonga.portfolioriskenginejava.tests.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

@TestConfiguration
public class ControllableClockConfig {
    @Bean
    @Primary
    public AtomicLong clockValue() {
        return new AtomicLong(1_700_000_000_000L);
    }

    @Bean
    public LongSupplier engineClock(AtomicLong clockValue) {
        return clockValue::get;
    }
}
