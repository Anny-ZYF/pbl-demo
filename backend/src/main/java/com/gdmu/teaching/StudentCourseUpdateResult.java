package com.gdmu.teaching;

import java.util.List;

public record StudentCourseUpdateResult(int success, int skipped, List<String> notFound) {
    public StudentCourseUpdateResult {
        notFound = List.copyOf(notFound);
    }
}
