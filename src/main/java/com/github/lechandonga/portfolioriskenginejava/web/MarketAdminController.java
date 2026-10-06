package com.github.lechandonga.portfolioriskenginejava.web;

import com.github.lechandonga.portfolioriskenginejava.dto.Requests.PublishQuoteRequest;
import com.github.lechandonga.portfolioriskenginejava.market.LocalMarketDataSource;
import com.github.lechandonga.portfolioriskenginejava.market.Quote;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.LongSupplier;

/** Test/admin endpoints for driving the local simulated market. */
@RestController
@RequestMapping("/api/market")
public class MarketAdminController {
    private final LocalMarketDataSource source;
    private final LongSupplier clock;

    public MarketAdminController(LocalMarketDataSource source, LongSupplier clock) {
        this.source = source;
        this.clock = clock;
    }

    @PostMapping("/quotes")
    public Map<String, Object> publish(@Valid @RequestBody PublishQuoteRequest request) {
        long asOf = request.asOf() != null ? request.asOf() : clock.getAsLong();
        source.publish(new Quote(request.symbol(), request.price(), asOf));
        return Map.of("symbol", request.symbol(), "price", request.price(), "asOf", asOf);
    }

    @PostMapping("/faults")
    public Map<String, Object> fault(@RequestParam String symbol,
                                     @RequestParam LocalMarketDataSource.Fault fault) {
        // Fault injection applies to the calling thread's simulated source access.
        source.setFault(fault);
        return Map.of("appliedToSymbol", symbol, "fault", fault);
    }
}
