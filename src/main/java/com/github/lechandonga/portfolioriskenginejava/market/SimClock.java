package com.github.lechandonga.portfolioriskenginejava.market;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 确定性模拟时钟：从固定纪元开始，只能向前推进。行情、下单时间戳都基于它，
 * 因此“相同输入 + 相同行情回放”可以复现完全相同的结果。
 */
public class SimClock {

    public static final Instant EPOCH = Instant.parse("2025-01-02T09:30:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(EPOCH);

    public Instant now() {
        return now.get();
    }

    /** 推进到 t；t 必须不早于当前时间，保证时间不倒退。 */
    public void advanceTo(Instant t) {
        now.updateAndGet(cur -> t.isBefore(cur) ? cur : t);
    }

    public void advanceSeconds(long seconds) {
        now.updateAndGet(cur -> cur.plusSeconds(seconds));
    }
}
