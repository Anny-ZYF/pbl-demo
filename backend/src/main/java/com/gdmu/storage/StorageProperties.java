package com.gdmu.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("app.storage")
public record StorageProperties(Path root) { }
