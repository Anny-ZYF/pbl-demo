package com.gdmu.storage;

public record StoredObject(String storageKey, String originalName, long size, String sha256) { }
