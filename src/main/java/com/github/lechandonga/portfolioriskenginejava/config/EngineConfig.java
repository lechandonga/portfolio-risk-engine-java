package com.github.lechandonga.portfolioriskenginejava.config;

import com.github.lechandonga.portfolioriskenginejava.market.LocalMarketDataSource;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataService;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.LongSupplier;

@Configuration
@EnableConfigurationProperties(EngineProperties.class)
public class EngineConfig {
    /** Engine clock. Overridable in tests for deterministic replay. */
    @Bean
    public LongSupplier engineClock() {
        return System::currentTimeMillis;
    }

    @Bean
    public LocalMarketDataSource localMarketDataSource(EngineProperties props) {
        return new LocalMarketDataSource(props.getMarket().getTimeoutMillis());
    }

    @Bean
    public MarketDataService marketDataService(MarketDataSource source, LongSupplier engineClock,
                                               EngineProperties props) {
        return new MarketDataService(source, engineClock,
                props.getMarket().getStalenessMillis(), props.getMarket().getTimeoutMillis());
    }
}
