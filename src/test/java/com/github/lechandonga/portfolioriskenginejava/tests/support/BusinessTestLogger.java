package com.github.lechandonga.portfolioriskenginejava.tests.support;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务覆盖日志扩展：每个用例通过/失败都打印“业务用例”日志，类结束时打印该业务模块汇总，
 * 使 mvn test 控制台可直接看出覆盖了哪些业务、各用例结果如何。
 * 通过 src/test/resources/junit-platform.properties 自动启用。
 */
public class BusinessTestLogger implements TestWatcher, AfterAllCallback {

    private static final Logger log = LoggerFactory.getLogger("BUSINESS-COVERAGE");
    private static final AtomicInteger passed = new AtomicInteger();
    private static final AtomicInteger failed = new AtomicInteger();

    @Override
    public void testSuccessful(ExtensionContext context) {
        passed.incrementAndGet();
        log.info("【业务用例】通过 ✓  {}", context.getDisplayName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        failed.incrementAndGet();
        log.error("【业务用例】失败 ✗  {} : {}", context.getDisplayName(),
                cause == null ? "" : cause.getMessage());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        log.info("【业务模块】{} —— 当前累计：通过 {}，失败 {}",
                context.getDisplayName(), passed.get(), failed.get());
    }
}
