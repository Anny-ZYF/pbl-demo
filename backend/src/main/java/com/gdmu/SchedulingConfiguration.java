package com.gdmu;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the deadline auto-lock sweeper only when that feature is switched on. */
@Configuration
@ConditionalOnProperty(name = "app.workload.auto-lock-enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfiguration {
}
