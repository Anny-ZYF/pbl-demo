package com.gdmu.scoring;

public record SubmissionContext(long submissionId, long assignmentId, long groupId, long teacherId,
                                int standardGroupSize, Double groupScore, Long scoringRuleId) { }
