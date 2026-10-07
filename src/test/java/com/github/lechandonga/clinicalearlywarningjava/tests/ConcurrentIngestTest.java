package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.ingest.IngestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 业务场景：同一患者并发上报不漏报、不重复告警、状态不错乱。
 */
@DisplayName("并发上报场景")
class ConcurrentIngestTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentIngestTest.class);

    private final TestSupport.Stack stack = new TestSupport.Stack();

    @Test
    @DisplayName("64 线程并发上报同一患者：全部接受、恰好一条告警")
    void concurrentIngestSamePatient() throws Exception {
        stack.registerV1();
        int threads = 64;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<IngestResult>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            // 一半异常一半正常，乱序并发到达
            double hr = idx % 2 == 0 ? 150 : 70;
            tasks.add(() -> stack.observationService.ingest(
                    TestSupport.obs("r" + idx, "P1", "HR", hr,
                            TestSupport.NOW.minusSeconds(idx))));
        }
        List<Future<IngestResult>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        int accepted = 0;
        for (Future<IngestResult> f : futures) {
            if (f.get().status() == IngestResult.Status.ACCEPTED) {
                accepted++;
            }
        }
        int stored = stack.observationService.observations("P1").size();
        long alerts = stack.alertService.byPatient("P1").size();
        log.info("用例[并发上报] 线程数={} 接受={} 库存={} 告警数={} 最终等级={}",
                threads, accepted, stored, alerts,
                stack.observationService.evaluationLog("P1").get(stack.observationService.evaluationLog("P1").size() - 1).level());
        assertEquals(threads, accepted);
        assertEquals(threads, stored);
        assertEquals(1, alerts);
    }

    @Test
    @DisplayName("并发重复上报同一 recordId：恰好接受一次")
    void concurrentDuplicateIngest() throws Exception {
        stack.registerV1();
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<IngestResult>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> stack.observationService.ingest(
                    TestSupport.obs("same-id", "P1", "HR", 150)));
        }
        List<Future<IngestResult>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        int accepted = 0, duplicated = 0;
        for (Future<IngestResult> f : futures) {
            switch (f.get().status()) {
                case ACCEPTED -> accepted++;
                case DUPLICATE -> duplicated++;
                default -> { }
            }
        }
        log.info("用例[并发重复上报] 线程数={} 接受={} 幂等忽略={} 库存={}",
                threads, accepted, duplicated, stack.observationService.observations("P1").size());
        assertEquals(1, accepted);
        assertEquals(threads - 1, duplicated);
        assertEquals(1, stack.observationService.observations("P1").size());
    }
}
