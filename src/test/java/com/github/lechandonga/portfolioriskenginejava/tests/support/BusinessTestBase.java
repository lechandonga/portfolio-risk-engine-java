package com.github.lechandonga.portfolioriskenginejava.tests.support;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logs which business module a test class covers and the pass/fail result of every
 * use case, so `mvn test` output reads as a business-coverage report.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BusinessTestBase {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    protected abstract String businessModule();

    @BeforeAll
    final void banner() {
        log.info("==================== 业务覆盖: {} ====================", businessModule());
    }

    @RegisterExtension
    final AfterTestExecutionCallback watcher = new AfterTestExecutionCallback() {
        @Override
        public void afterTestExecution(ExtensionContext context) {
            String name = context.getDisplayName();
            Throwable error = context.getExecutionException().orElse(null);
            if (error == null) {
                passed.incrementAndGet();
                log.info("[用例通过] {} | {}", businessModule(), name);
            } else {
                failed.incrementAndGet();
                log.warn("[用例失败] {} | {} | {}", businessModule(), name, error.toString());
            }
        }
    };

    @AfterAll
    final void summary() {
        log.info("-------------------- {} 测试汇总: 通过 {} 个, 失败 {} 个 --------------------",
                businessModule(), passed.get(), failed.get());
    }
}
