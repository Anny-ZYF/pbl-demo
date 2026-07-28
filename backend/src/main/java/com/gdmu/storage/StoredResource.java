package com.gdmu.storage;

import org.springframework.core.io.Resource;

public record StoredResource(Resource resource, String filename, long size, String contentType) { }
