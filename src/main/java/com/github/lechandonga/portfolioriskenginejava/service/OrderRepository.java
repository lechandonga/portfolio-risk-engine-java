package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class OrderRepository {
    private final Map<String, Order> byOrderId = new ConcurrentHashMap<>();
    /** Key: accountId + clientRequestId -> the single order ever created for that idempotency key. */
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    private static String idemKey(String accountId, String clientRequestId) {
        return accountId + '\u0000' + clientRequestId;
    }

    public Order save(Order order) {
        byOrderId.put(order.getOrderId(), order);
        if (order.getClientRequestId() != null) {
            idempotencyIndex.putIfAbsent(idemKey(order.getAccountId(), order.getClientRequestId()),
                    order.getOrderId());
        }
        return order;
    }

    public Order get(String orderId) {
        return byOrderId.get(orderId);
    }

    public Order findByIdempotencyKey(String accountId, String clientRequestId) {
        String orderId = idempotencyIndex.get(idemKey(accountId, clientRequestId));
        return orderId == null ? null : byOrderId.get(orderId);
    }

    public List<Order> findByAccount(String accountId) {
        return byOrderId.values().stream().filter(o -> o.getAccountId().equals(accountId)).toList();
    }
}
