package com.github.lechandonga.portfolioriskenginejava.api;

import com.github.lechandonga.portfolioriskenginejava.market.MarketReplayService;
import com.github.lechandonga.portfolioriskenginejava.market.MarketDataException;
import com.github.lechandonga.portfolioriskenginejava.market.Quote;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketReplayService service;

    public MarketController(MarketReplayService service) {
        this.service = service;
    }

    /** 推进一步确定性随机游走行情。 */
    @PostMapping("/refresh")
    public List<Quote> refresh() {
        return service.refresh();
    }

    /** 按给定时间戳序列回放行情；时间戳必须严格递增。 */
    @PostMapping("/replay")
    public List<Quote> replay(@RequestBody List<MarketReplayService.ReplayTick> ticks) {
        return service.replay(ticks);
    }

    /** 测试支持：注入行情故障（TIMEOUT/DIRTY/UNAVAILABLE），传 null 清除。 */
    @PostMapping("/faults")
    public String inject(@RequestParam(required = false) MarketDataException.Kind kind) {
        if (kind == null) {
            service.clearFailure();
            return "cleared";
        }
        service.injectFailure(kind);
        return "injected:" + kind;
    }
}
