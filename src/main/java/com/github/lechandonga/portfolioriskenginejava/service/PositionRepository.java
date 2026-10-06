package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.domain.Position;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class PositionRepository {
    private final Map<String, Position> positions = new ConcurrentHashMap<>();

    private static String key(String accountId, String symbol) {
        return accountId + '\u0000' + symbol;
    }

    public Position get(String accountId, String symbol) {
        return positions.get(key(accountId, symbol));
    }

    public Position getOrCreate(String accountId, String symbol) {
        return positions.computeIfAbsent(key(accountId, symbol), k -> new Position(accountId, symbol));
    }

    public List<Position> findByAccount(String accountId) {
        List<Position> result = new ArrayList<>();
        for (Position p : positions.values()) {
            if (p.getAccountId().equals(accountId)) {
                result.add(p);
            }
        }
        return result;
    }
}
