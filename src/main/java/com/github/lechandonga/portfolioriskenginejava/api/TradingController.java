package com.github.lechandonga.portfolioriskenginejava.api;

import com.github.lechandonga.portfolioriskenginejava.api.dto.OrderDto;
import com.github.lechandonga.portfolioriskenginejava.api.dto.OrderRequestDto;
import com.github.lechandonga.portfolioriskenginejava.trading.OrderRequest;
import com.github.lechandonga.portfolioriskenginejava.trading.TradingEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts/{accountId}/orders")
public class TradingController {

    private final TradingEngine tradingEngine;

    public TradingController(TradingEngine tradingEngine) {
        this.tradingEngine = tradingEngine;
    }

    @PostMapping
    public OrderDto submit(@PathVariable String accountId,
                           @RequestBody OrderRequestDto dto) {
        return OrderDto.from(tradingEngine.submit(new OrderRequest(
                accountId, dto.clientRequestId(), dto.symbol(), dto.side(),
                dto.type(), dto.quantity(), dto.limitPrice())));
    }

    @PostMapping("/{orderId}/cancel")
    public OrderDto cancel(@PathVariable String accountId, @PathVariable long orderId) {
        return OrderDto.from(tradingEngine.cancel(accountId, orderId));
    }

    @GetMapping("/{orderId}")
    public OrderDto get(@PathVariable String accountId, @PathVariable long orderId) {
        return OrderDto.from(tradingEngine.getOrder(accountId, orderId));
    }

    @GetMapping
    public List<OrderDto> list(@PathVariable String accountId) {
        return tradingEngine.listOrders(accountId).stream().map(OrderDto::from).toList();
    }
}
