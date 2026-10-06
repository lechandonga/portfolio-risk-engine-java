package com.github.lechandonga.portfolioriskenginejava.config;

import com.github.lechandonga.portfolioriskenginejava.corporate.CorporateActionService;
import com.github.lechandonga.portfolioriskenginejava.market.MarketReplayService;
import com.github.lechandonga.portfolioriskenginejava.market.SimClock;
import com.github.lechandonga.portfolioriskenginejava.market.SimulatedMarketDataProvider;
import com.github.lechandonga.portfolioriskenginejava.market.ValidatingMarketDataProvider;
import com.github.lechandonga.portfolioriskenginejava.risk.RiskConfidence;
import com.github.lechandonga.portfolioriskenginejava.risk.ValuationService;
import com.github.lechandonga.portfolioriskenginejava.store.AccountStore;
import com.github.lechandonga.portfolioriskenginejava.trading.FeePolicy;
import com.github.lechandonga.portfolioriskenginejava.trading.TradingEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(EngineProperties.class)
public class EngineConfig {

    private static final Map<String, BigDecimal> SEED_UNIVERSE = new LinkedHashMap<>();

    static {
        SEED_UNIVERSE.put("AAPL", new BigDecimal("100.00"));
        SEED_UNIVERSE.put("MSFT", new BigDecimal("250.00"));
        SEED_UNIVERSE.put("600519", new BigDecimal("1500.00"));
        SEED_UNIVERSE.put("000001", new BigDecimal("12.00"));
    }

    @Bean
    public SimClock simClock() {
        return new SimClock();
    }

    @Bean
    public SimulatedMarketDataProvider rawMarketDataProvider(SimClock clock,
                                                             EngineProperties properties) {
        SimulatedMarketDataProvider provider = new SimulatedMarketDataProvider(clock);
        SEED_UNIVERSE.forEach((symbol, initial) ->
                provider.registerSymbol(symbol, initial,
                        seedHistory(symbol, initial, properties.getSeedHistorySize())));
        return provider;
    }

    @Bean
    public ValidatingMarketDataProvider marketDataProvider(
            SimulatedMarketDataProvider raw, SimClock clock, EngineProperties properties) {
        return new ValidatingMarketDataProvider(raw, clock,
                Duration.ofSeconds(properties.getMaxQuoteAgeSeconds()));
    }

    @Bean
    public AccountStore accountStore(EngineProperties properties) {
        AccountStore store = new AccountStore();
        store.createAccount("demo", properties.getDemoInitialCash());
        return store;
    }

    @Bean
    public FeePolicy feePolicy(EngineProperties properties) {
        return new FeePolicy(properties.getFeeRate(), properties.getMinFee());
    }

    @Bean
    public TradingEngine tradingEngine(AccountStore store,
                                       ValidatingMarketDataProvider marketData,
                                       FeePolicy feePolicy, SimClock clock) {
        return new TradingEngine(store, marketData, feePolicy, clock);
    }

    @Bean
    public ValuationService valuationService(AccountStore store,
                                             ValidatingMarketDataProvider marketData) {
        return new ValuationService(store, marketData);
    }

    @Bean
    public CorporateActionService corporateActionService(AccountStore store) {
        return new CorporateActionService(store);
    }

    @Bean
    public MarketReplayService marketReplayService(SimulatedMarketDataProvider raw) {
        return new MarketReplayService(raw);
    }

    @Bean
    public RiskConfidence defaultRiskConfidence(EngineProperties properties) {
        return RiskConfidence.of(properties.getConfidence(),
                properties.getMinHistorySamples());
    }

    /** 确定性种子序列：固定公式生成的正弦路径，启动即保证可复现且样本充足。 */
    private static List<BigDecimal> seedHistory(String symbol, BigDecimal initial, int size) {
        List<BigDecimal> history = new ArrayList<>();
        double base = initial.doubleValue();
        double phase = (symbol.hashCode() & 0xffff) / 100.0d;
        for (int i = 0; i < size; i++) {
            double wave = Math.sin(i / 5.0d + phase) * 0.01d;
            double drift = (i - size) * 0.0001d;
            double price = base * (1.0d + wave + drift);
            history.add(BigDecimal.valueOf(price).max(BigDecimal.valueOf(0.01d))
                    .round(MathContext.DECIMAL64));
        }
        return history;
    }
}
