package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.domain.Instrument;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InstrumentRepository {
    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();

    public Instrument get(String symbol) {
        return instruments.get(symbol);
    }

    public Instrument save(Instrument instrument) {
        instruments.put(instrument.getSymbol(), instrument);
        return instrument;
    }

    public Collection<Instrument> findAll() {
        return instruments.values();
    }
}
