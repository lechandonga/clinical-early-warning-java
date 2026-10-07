package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertStatus;
import com.github.lechandonga.clinicalearlywarningjava.exception.ValidationException;
import com.github.lechandonga.clinicalearlywarningjava.service.ObservationService;
import com.github.lechandonga.clinicalearlywarningjava.store.AlertStore;
import com.github.lechandonga.clinicalearlywarningjava.tests.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：并发上报一致性。
 * - 多线程并发上报同一患者：不漏算、不产生重复告警、状态合法；
 * - 多线程并发提交完全相同的报文：恰好一条被接受，其余以 DUPLICATE_RECORD 拒绝。
 */
@SpringBootTest
@Import(TestClockConfig.class)
class ConcurrentObservationTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentObservationTest.class);

    @Autowired
    private ObservationService observationService;
    @Autowired
    private AlertStore alertStore;

    @Test
    @DisplayName("同一患者 24 条异常数据并发上报: 恰好 1 条未关闭告警且状态一致")
    void concurrentReportsProduceSingleConsistentAlert() throws InterruptedException {
        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    double value = idx % 3 == 0 ? 88.0 : 90.0;
                    observationService.ingest(
                            "C-" + idx + "-" + UUID.randomUUID(),
                            "P-CONC",
                            "O2_SAT",
                            value,
                            Instant.parse("2026-10-07T13:00:00Z").plusSeconds(idx));
                    accepted.incrementAndGet();
                } catch (ValidationException ex) {
                    rejected.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                } catch (Exception ex) {
                    log.error("并发出乎意料的异常", ex);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(threads, accepted.get() + rejected.get());
        List<Alert> alerts = alertStore.listByPatient("P-CONC");
        assertEquals(1, alerts.size(), "并发上报不得产生重复告警");
        Alert open = alertStore.findOpen("P-CONC").orElseThrow();
        assertTrue(open.getStatus() == AlertStatus.ACTIVE || open.getStatus() == AlertStatus.ESCALATED,
                "状态只能为 ACTIVE/ESCALATED，实际=" + open.getStatus());
        log.info("[并发上报] 用例-同患者 {} 线程并发 => 测试通过: 接受={} 拒绝={} 告警数=1 状态={} 评分={}",
                threads, accepted.get(), rejected.get(), open.getStatus(), open.getLastScore());
    }

    @Test
    @DisplayName("同一报文 16 线程并发重复提交: 恰好 1 条接受，其余 DUPLICATE_RECORD")
    void concurrentIdenticalSubmissionsAreIdempotent() throws InterruptedException {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        List<Exception> unexpected = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    observationService.ingest("SAME-RECORD-ID", "P-DUP-CONC", "TEMP_C", 34.0,
                            Instant.parse("2026-10-07T14:00:00Z"));
                    accepted.incrementAndGet();
                } catch (ValidationException ex) {
                    if ("DUPLICATE_RECORD".equals(ex.getCode())) {
                        duplicates.incrementAndGet();
                    } else {
                        unexpected.add(ex);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    unexpected.add(ex);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertTrue(unexpected.isEmpty(), "不应出现非重复类异常: " + unexpected);
        assertEquals(1, accepted.get(), "相同报文只允许一条被接受");
        assertEquals(threads - 1, duplicates.get());
        assertEquals(1, alertStore.listByPatient("P-DUP-CONC").size());
        log.info("[并发上报] 用例-相同报文 {} 线程并发 => 测试通过: 接受 1 / 重复拒绝 {}",
                threads, duplicates.get());
    }
}
