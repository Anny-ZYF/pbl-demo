package com.gdmu.scoring;

import java.time.LocalDateTime;

public record PublishedScoreRow(long id, long studentId, String status, LocalDateTime publishedAt) { }
