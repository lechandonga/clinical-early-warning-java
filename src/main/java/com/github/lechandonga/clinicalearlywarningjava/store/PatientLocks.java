package com.github.lechandonga.clinicalearlywarningjava.store;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 患者级串行化：同一患者的上报 / 判定 / 告警流转在同一把锁内完成，
 * 保证并发与重复上报下不漏报、不重告警、状态不错乱；不同患者之间仍可并行。
 */
@Component
public class PatientLocks {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(String patientId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(patientId, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void withLock(String patientId, Runnable action) {
        withLock(patientId, () -> {
            action.run();
            return null;
        });
    }
}
