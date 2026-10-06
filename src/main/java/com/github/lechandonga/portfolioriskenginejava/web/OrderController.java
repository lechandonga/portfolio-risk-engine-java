package com.github.lechandonga.portfolioriskenginejava.web;

import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.dto.Responses.OrderView;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.service.OrderRepository;
import com.github.lechandonga.portfolioriskenginejava.service.TradingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts/{accountId}/orders")
public class OrderController {
    private final TradingService trading;
    private final OrderRepository orders;

    public OrderController(TradingService trading, OrderRepository orders) {
        this.trading = trading;
        this.orders = orders;
    }

    @PostMapping
    public OrderView submit(@PathVariable String accountId, @Valid @RequestBody OrderRequest request) {
        return OrderView.from(trading.submit(accountId, request));
    }

    @PostMapping("/{orderId}/fill")
    public OrderView fill(@PathVariable String accountId, @PathVariable String orderId) {
        return OrderView.from(trading.fill(accountId, orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public OrderView cancel(@PathVariable String accountId, @PathVariable String orderId) {
        return OrderView.from(trading.cancel(accountId, orderId));
    }

    @GetMapping("/{orderId}")
    public OrderView get(@PathVariable String accountId, @PathVariable String orderId) {
        Order order = orders.get(orderId);
        if (order == null || !order.getAccountId().equals(accountId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND,
                    "order not found for account " + accountId + ": " + orderId);
        }
        return OrderView.from(order);
    }

    @GetMapping
    public List<OrderView> list(@PathVariable String accountId) {
        return orders.findByAccount(accountId).stream().map(OrderView::from).toList();
    }
}
