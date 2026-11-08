package com.joutio.bff.infrastructure.config;

import com.joutio.bff.application.ExpoPushService;
import com.joutio.bff.application.ExpoPushStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Configuration
@EnableScheduling
public class ExpoPushWorker {
    private final ExpoPushService service;
    private final ExpoPushStore store;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("expo-push-worker").factory());
    public ExpoPushWorker(ExpoPushService service, ExpoPushStore store) { this.service=service; this.store=store; }
    @Scheduled(fixedDelayString="${joutio.expo-push.poll-ms:2000}") public void deliver() {
        if (!running.compareAndSet(false, true)) return;
        executor.execute(() -> { try { service.drain(); } finally { running.set(false); } });
    }
    @PreDestroy public void stop() { executor.shutdownNow(); }
    @Scheduled(fixedDelay=86400000) public void purge() { store.purge(Instant.now().minus(7, ChronoUnit.DAYS)); }
}
