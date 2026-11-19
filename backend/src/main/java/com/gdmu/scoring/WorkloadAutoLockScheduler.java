package com.gdmu.scoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic sweep for assignments that have passed their deadline. Any workload version that is
 * still waiting for confirmations is locked so scoring can proceed. Reads also trigger the same
 * auto-lock lazily, so the sweep is a consistency backstop rather than the only mechanism.
 */
@Component
@ConditionalOnProperty(name = "app.workload.auto-lock-enabled", havingValue = "true", matchIfMissing = true)
public class WorkloadAutoLockScheduler {
    private static final Logger log = LoggerFactory.getLogger(WorkloadAutoLockScheduler.class);
    private final ScoringService service;

    public WorkloadAutoLockScheduler(ScoringService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "${app.workload.auto-lock-initial-delay-ms:10000}",
                fixedDelayString = "${app.workload.auto-lock-interval-ms:60000}")
    public void autoLockExpiredWorkloads() {
        try {
            int locked = service.autoLockAllExpired();
            if (locked > 0) {
                log.info("Auto-locked {} workload sets after their assignment deadlines", locked);
            }
        } catch (RuntimeException ex) {
            log.warn("Deadline workload auto-lock sweep failed: {}", ex.getMessage());
        }
    }
}
