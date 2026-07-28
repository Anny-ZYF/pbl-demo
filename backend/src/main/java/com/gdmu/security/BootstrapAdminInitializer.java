package com.gdmu.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminInitializer {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private final AccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    public BootstrapAdminInitializer(AccountMapper mapper, PasswordEncoder passwordEncoder,
                                     BootstrapAdminProperties properties) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeOnce() {
        if (mapper.adminCount() > 0) return;
        if (properties.username() == null || properties.username().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            log.warn("No administrator exists. Set BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD for the first start.");
            return;
        }
        if (!properties.password().matches("^(?=.*[A-Za-z])(?=.*\\d).{12,64}$")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be 12-64 characters and contain letters and digits");
        }
        String displayName = properties.displayName() == null || properties.displayName().isBlank()
                ? "系统管理员" : properties.displayName().trim();
        mapper.insertBootstrapAdmin(properties.username().trim(), passwordEncoder.encode(properties.password()), displayName);
        log.info("One-time bootstrap administrator '{}' created; password change is required on first login",
                properties.username().trim());
    }
}
