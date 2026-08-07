package com.gdmu.scoring;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface ScoringMapper {
    @Update("""
            UPDATE submission s JOIN assignment a ON a.id=s.assignment_id
            SET s.teacher_score=#{score},s.teacher_comment=#{comment},s.graded_by=#{teacherId},
                s.graded_time=CURRENT_TIMESTAMP,s.status='GRADED',s.updated_at=CURRENT_TIMESTAMP
            WHERE s.id=#{submissionId} AND a.teacher_id=#{teacherId}
              AND s.is_latest=1 AND s.status IN ('SUBMITTED','RETURNED','GRADED') AND s.is_score_published=0
            """)
    int gradeSubmission(@Param("submissionId") long submissionId, @Param("teacherId") long teacherId,
                        @Param("score") double score, @Param("comment") String comment);

    @Insert("""
            INSERT INTO individual_score(submission_id,student_id,teacher_score,contribution_ratio,individual_final_score,calculated_by,is_published,publish_time)
            SELECT s.id,gm.student_id,s.teacher_score,100.0000,s.teacher_score,#{teacherId},1,CURRENT_TIMESTAMP
            FROM submission s JOIN group_member gm ON gm.group_id=s.group_id AND gm.assignment_id=s.assignment_id AND gm.status='ACTIVE'
            WHERE s.id=#{submissionId}
            ON DUPLICATE KEY UPDATE teacher_score=VALUES(teacher_score),individual_final_score=VALUES(individual_final_score),
              calculated_by=VALUES(calculated_by),is_published=1,publish_time=CURRENT_TIMESTAMP
            """)
    int fallbackIndividualScores(@Param("submissionId") long submissionId, @Param("teacherId") long teacherId);

    @Select("""
            SELECT s.id AS submissionId,a.id AS assignmentId,s.group_id AS groupId,a.teacher_id AS teacherId,
                   a.standard_group_size AS standardGroupSize,
                   (SELECT COUNT(*) FROM group_member gm WHERE gm.group_id=s.group_id AND gm.status='ACTIVE') AS actualGroupSize,
                   s.teacher_score AS groupScore,
                   COALESCE(a.scoring_rule_id,(SELECT id FROM scoring_rule WHERE status='ACTIVE' ORDER BY id LIMIT 1)) AS scoringRuleId
            FROM submission s JOIN assignment a ON a.id=s.assignment_id
            WHERE s.id=#{submissionId} AND a.teacher_id=#{teacherId}
              AND s.is_latest=1 AND s.status='GRADED' AND s.teacher_score IS NOT NULL
            """)
    SubmissionContext submissionContext(@Param("submissionId") long submissionId,
                                        @Param("teacherId") long teacherId);

    @Select("""
            SELECT id,part_name AS name,weight FROM assignment_parts
            WHERE assignment_id=#{assignmentId} ORDER BY sort_order,id
            """)
    List<PartRow> parts(long assignmentId);

    @Select("""
            SELECT id FROM workload_set
            WHERE assignment_id=#{assignmentId} AND group_id=#{groupId} AND status='LOCKED'
            ORDER BY version_no DESC LIMIT 1
            """)
    Long lockedWorkloadSet(@Param("assignmentId") long assignmentId, @Param("groupId") long groupId);

    @Select("""
            SELECT assignment_part_id AS partId,student_id AS studentId,share
            FROM workload_item WHERE workload_set_id=#{workloadSetId}
            ORDER BY assignment_part_id,student_id
            """)
    List<AllocationRow> allocations(long workloadSetId);

    @Select("""
            SELECT gm.student_id AS studentId,s.name AS studentName
            FROM group_member gm JOIN student_users s ON s.id=gm.student_id
            WHERE gm.group_id=#{groupId} AND gm.status='ACTIVE' ORDER BY gm.student_id
            """)
    List<MemberRow> members(long groupId);

    @Select("""
            SELECT COUNT(*) FROM group_table g JOIN assignment a ON a.id=g.assignment_id
            WHERE g.id=#{groupId} AND g.assignment_id=#{assignmentId} AND a.teacher_id=#{teacherId}
            """)
    int teacherOwnsGroup(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId,
                         @Param("teacherId") long teacherId);

    @Select("""
            SELECT COUNT(*) FROM group_table
            WHERE id=#{groupId} AND assignment_id=#{assignmentId} AND leader_student_id=#{studentId}
              AND team_status IN ('CONFIRMED','COMPLETED')
            """)
    int studentLeadsGroup(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId,
                           @Param("studentId") long studentId);

    @Select("""
            SELECT id FROM group_table
            WHERE id=#{groupId} AND assignment_id=#{assignmentId}
            FOR UPDATE
            """)
    Long lockGroupForWorkload(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId);

    @Select("""
            SELECT COALESCE(MAX(version_no),0)+1 FROM workload_set
            WHERE assignment_id=#{assignmentId} AND group_id=#{groupId}
            """)
    int nextWorkloadVersion(@Param("assignmentId") long assignmentId, @Param("groupId") long groupId);

    @Update("""
            UPDATE workload_set SET status='SUPERSEDED',updated_at=CURRENT_TIMESTAMP
            WHERE group_id=#{groupId} AND assignment_id=#{assignmentId}
              AND status IN ('DRAFT','PENDING_CONFIRMATION')
            """)
    int supersedeOpenWorkloads(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId);

    @Insert("""
            INSERT INTO workload_set(group_id,assignment_id,version_no,status,created_by_teacher_id,
              submitted_at,created_at,updated_at)
            VALUES(#{groupId},#{assignmentId},#{version},'PENDING_CONFIRMATION',#{teacherId},CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertWorkloadSet(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId,
                          @Param("version") int version, @Param("teacherId") long teacherId);

    @Insert("""
            INSERT INTO workload_set(group_id,assignment_id,version_no,status,created_by_student_id,
              submitted_at,created_at,updated_at)
            VALUES(#{groupId},#{assignmentId},#{version},'PENDING_CONFIRMATION',#{studentId},CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertStudentWorkloadSet(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId,
                                 @Param("version") int version, @Param("studentId") long studentId);

    @Select("""
            SELECT id FROM workload_set WHERE group_id=#{groupId} AND assignment_id=#{assignmentId} AND version_no=#{version}
            """)
    long workloadSetId(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId,
                       @Param("version") int version);

    @Insert("""
            INSERT INTO workload_item(workload_set_id,assignment_part_id,student_id,share,evidence_json,created_at,updated_at)
            VALUES(#{setId},#{partId},#{studentId},#{share},#{evidence},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertWorkloadItem(@Param("setId") long setId, @Param("partId") long partId,
                           @Param("studentId") long studentId, @Param("share") double share,
                           @Param("evidence") String evidence);

    @Insert("""
            INSERT INTO workload_confirmation(workload_set_id,student_id,status,created_at,updated_at)
            SELECT #{setId},gm.student_id,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
            FROM group_member gm WHERE gm.group_id=#{groupId} AND gm.status='ACTIVE'
            """)
    int insertConfirmations(@Param("setId") long setId, @Param("groupId") long groupId);

    @Select("""
            SELECT ws.id,ws.version_no AS versionNo,ws.status,ws.submitted_at AS submittedAt,
                   wc.status AS myConfirmation,wc.comment
            FROM workload_set ws JOIN workload_confirmation wc ON wc.workload_set_id=ws.id
            WHERE ws.assignment_id=#{assignmentId} AND wc.student_id=#{studentId}
            ORDER BY ws.version_no DESC LIMIT 1
            """)
    Map<String, Object> studentWorkload(@Param("assignmentId") long assignmentId,
                                        @Param("studentId") long studentId);

    @Select("""
            SELECT ws.id,ws.version_no AS versionNo,ws.status,ws.submitted_at AS submittedAt,ws.locked_at AS lockedAt
            FROM workload_set ws JOIN assignment a ON a.id=ws.assignment_id
            WHERE ws.assignment_id=#{assignmentId} AND ws.group_id=#{groupId} AND a.teacher_id=#{teacherId}
            ORDER BY ws.version_no DESC LIMIT 1
            """)
    Map<String, Object> teacherWorkload(@Param("assignmentId") long assignmentId,
                                        @Param("groupId") long groupId,
                                        @Param("teacherId") long teacherId);

    @Select("""
            SELECT wc.student_id AS studentId,s.name AS studentName,wc.status,wc.comment,wc.confirmed_at AS confirmedAt
            FROM workload_confirmation wc JOIN student_users s ON s.id=wc.student_id
            WHERE wc.workload_set_id=#{setId} ORDER BY s.student_user_id
            """)
    List<Map<String, Object>> workloadConfirmations(long setId);

    @Select("""
            SELECT wi.assignment_part_id AS assignmentPartId,ap.part_name AS partName,wi.student_id AS studentId,
                   s.name AS studentName,wi.share,wi.evidence_json AS evidence
            FROM workload_item wi JOIN assignment_parts ap ON ap.id=wi.assignment_part_id
            JOIN student_users s ON s.id=wi.student_id
            WHERE wi.workload_set_id=#{setId} ORDER BY ap.sort_order,wi.student_id
            """)
    List<Map<String, Object>> workloadItems(long setId);

    @Select("""
            SELECT ws.id FROM workload_set ws
            JOIN workload_confirmation wc ON wc.workload_set_id=ws.id
            WHERE ws.id=#{setId} AND wc.student_id=#{studentId}
              AND ws.status='PENDING_CONFIRMATION'
            FOR UPDATE
            """)
    Long lockWorkloadSetForConfirmation(@Param("setId") long setId,
                                        @Param("studentId") long studentId);

    @Update("""
            UPDATE workload_confirmation wc JOIN workload_set ws ON ws.id=wc.workload_set_id
            SET wc.status=#{status},wc.comment=#{comment},wc.confirmed_at=CURRENT_TIMESTAMP,wc.updated_at=CURRENT_TIMESTAMP
            WHERE wc.workload_set_id=#{setId} AND wc.student_id=#{studentId} AND ws.status='PENDING_CONFIRMATION'
            """)
    int confirmWorkload(@Param("setId") long setId, @Param("studentId") long studentId,
                        @Param("status") String status, @Param("comment") String comment);

    @Select("SELECT COUNT(*) FROM workload_confirmation WHERE workload_set_id=#{setId} AND status != 'CONFIRMED'")
    int unconfirmedCount(long setId);

    @Update("""
            UPDATE workload_set old_set JOIN workload_set current_set
              ON current_set.id=#{setId} AND old_set.group_id=current_set.group_id
              AND old_set.assignment_id=current_set.assignment_id
            SET old_set.status='SUPERSEDED',old_set.updated_at=CURRENT_TIMESTAMP
            WHERE old_set.id!=current_set.id AND old_set.status='LOCKED'
            """)
    int supersedePreviousLocked(long setId);

    @Update("""
            UPDATE workload_set SET status='LOCKED',locked_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
            WHERE id=#{setId} AND status='PENDING_CONFIRMATION'
            """)
    int lockWorkload(long setId);

    @Select("""
            SELECT mv.id,mv.version,mv.artifact_sha256 AS artifactSha256 FROM model_version mv
            WHERE mv.status='ACTIVE' AND mv.algorithm='XGBOOST' AND mv.model_key='paper-score-p4'
            ORDER BY mv.activated_at DESC LIMIT 1
            """)
    ActiveModelRow activeModel();

    @Insert("""
            INSERT INTO scoring_run(run_no,assignment_id,group_id,submission_id,scoring_rule_id,model_version_id,
              status,standard_group_size,actual_group_size,group_score,input_hash,triggered_by_teacher_id,
              started_at,created_at,updated_at)
            VALUES(#{runNo},#{c.assignmentId},#{c.groupId},#{c.submissionId},#{c.scoringRuleId},#{modelId},
              'CREATED',#{c.standardGroupSize},#{c.actualGroupSize},#{c.groupScore},#{inputHash},#{c.teacherId},
              CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertRun(@Param("runNo") String runNo, @Param("c") SubmissionContext context,
                  @Param("modelId") Long modelId, @Param("inputHash") String inputHash);

    @Select("SELECT id FROM scoring_run WHERE run_no=#{runNo}")
    long runId(String runNo);

    @Insert("""
            INSERT INTO scoring_input_snapshot(scoring_run_id,student_id,contribution,standard_group_size,
              actual_group_size,group_score,feature_schema_version,features_json,source_snapshot_json,feature_hash,created_at)
            VALUES(#{runId},#{studentId},#{contribution},#{standardGroupSize},#{actualGroupSize},#{groupScore},
              'paper-mcqs-v1',CAST(#{featuresJson} AS JSON),CAST(#{sourceJson} AS JSON),#{featureHash},CURRENT_TIMESTAMP)
            """)
    int insertSnapshot(@Param("runId") long runId, @Param("studentId") long studentId,
                       @Param("contribution") double contribution,
                       @Param("standardGroupSize") int standardGroupSize,
                       @Param("actualGroupSize") int actualGroupSize,
                       @Param("groupScore") double groupScore,
                       @Param("featuresJson") String featuresJson, @Param("sourceJson") String sourceJson,
                       @Param("featureHash") String featureHash);

    @Insert("""
            INSERT INTO score_result(scoring_run_id,student_id,contribution,group_score,rule_score,model_score,
              model_rule_delta,anomaly_flag,final_score,status,explanation_json,created_at,updated_at)
            VALUES(#{runId},#{studentId},#{contribution},#{groupScore},#{ruleScore},#{modelScore},#{delta},#{anomaly},
              #{suggestion},'DRAFT',CAST(#{explanationJson} AS JSON),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertResult(@Param("runId") long runId, @Param("studentId") long studentId,
                     @Param("contribution") double contribution, @Param("groupScore") double groupScore,
                     @Param("ruleScore") double ruleScore, @Param("modelScore") Double modelScore,
                     @Param("delta") Double delta, @Param("anomaly") boolean anomaly,
                     @Param("suggestion") double suggestion, @Param("explanationJson") String explanationJson);

    @Update("""
            UPDATE scoring_run SET status=#{status},error_message=#{errorMessage},completed_at=CURRENT_TIMESTAMP,
              updated_at=CURRENT_TIMESTAMP WHERE id=#{runId}
            """)
    int completeRun(@Param("runId") long runId, @Param("status") String status,
                    @Param("errorMessage") String errorMessage);

    @Select("""
            SELECT sr.id,sr.run_no AS runNo,sr.status,sr.assignment_id AS assignmentId,sr.group_id AS groupId,
                   sr.model_version_id AS modelVersionId,sr.input_hash AS inputHash,sr.error_message AS errorMessage,
                   sr.started_at AS startedAt,sr.completed_at AS completedAt
            FROM scoring_run sr JOIN assignment a ON a.id=sr.assignment_id
            WHERE sr.id=#{runId} AND a.teacher_id=#{teacherId}
            """)
    Map<String, Object> teacherRun(@Param("runId") long runId, @Param("teacherId") long teacherId);

    @Select("""
            SELECT r.id,r.student_id AS studentId,s.name AS studentName,r.contribution,r.group_score AS groupScore,
                   r.rule_score AS ruleScore,r.model_score AS modelScore,r.model_rule_delta AS modelRuleDelta,
                   r.anomaly_flag AS anomaly,r.final_score AS finalScore,r.status,r.explanation_json AS explanation,
                   r.review_reason AS reviewReason
            FROM score_result r JOIN student_users s ON s.id=r.student_id
            WHERE r.scoring_run_id=#{runId} ORDER BY s.student_user_id
            """)
    List<Map<String, Object>> runResults(long runId);

    @Select("SELECT final_score FROM score_result WHERE id=#{resultId} AND scoring_run_id=#{runId}")
    Double currentSuggestedScore(@Param("resultId") long resultId, @Param("runId") long runId);

    @Update("""
            UPDATE score_result r JOIN scoring_run sr ON sr.id=r.scoring_run_id JOIN assignment a ON a.id=sr.assignment_id
            SET r.manual_adjustment=ROUND(#{score}-r.final_score,2),r.final_score=#{score},
                r.review_reason=#{reason},r.reviewed_by_teacher_id=#{teacherId},r.reviewed_at=CURRENT_TIMESTAMP,
                r.status='REVIEWED',r.updated_at=CURRENT_TIMESTAMP
            WHERE r.id=#{resultId} AND sr.id=#{runId} AND a.teacher_id=#{teacherId}
              AND sr.status IN ('MODEL_COMPLETE','DEGRADED') AND r.status='DRAFT'
            """)
    int reviewResult(@Param("runId") long runId, @Param("resultId") long resultId,
                     @Param("teacherId") long teacherId, @Param("score") double score,
                     @Param("reason") String reason);

    @Select("SELECT COUNT(*) FROM score_result WHERE scoring_run_id=#{runId} AND status != 'REVIEWED'")
    int unreviewedResultCount(long runId);

    @Update("""
            UPDATE scoring_run sr JOIN assignment a ON a.id=sr.assignment_id
            SET sr.status='APPROVED',sr.updated_at=CURRENT_TIMESTAMP
            WHERE sr.id=#{runId} AND a.teacher_id=#{teacherId} AND sr.status IN ('MODEL_COMPLETE','DEGRADED')
            """)
    int approveRun(@Param("runId") long runId, @Param("teacherId") long teacherId);

    @Update("""
            UPDATE score_result r JOIN scoring_run sr ON sr.id=r.scoring_run_id
              JOIN scoring_run source ON source.assignment_id=sr.assignment_id AND source.id=#{runId}
            SET r.status='PUBLISHED',r.published_at=CURRENT_TIMESTAMP,r.updated_at=CURRENT_TIMESTAMP
            WHERE r.status IN ('DRAFT','REVIEWED','PENDING_REVIEW','APPROVED','PUBLISHED')
            """)
    int publishResults(long runId);

    @Update("""
            UPDATE scoring_run target JOIN scoring_run source ON source.assignment_id=target.assignment_id AND source.id=#{runId}
            JOIN assignment a ON a.id=target.assignment_id
            SET target.status='PUBLISHED',target.updated_at=CURRENT_TIMESTAMP
            WHERE a.teacher_id=#{teacherId}
              AND target.status IN ('MODEL_COMPLETE','DEGRADED','APPROVED','PUBLISHED')
            """)
    int publishRun(@Param("runId") long runId, @Param("teacherId") long teacherId);

    @Select("""
            SELECT COUNT(*) FROM scoring_run sr
            JOIN assignment a ON a.id=sr.assignment_id
            WHERE sr.id=#{runId} AND a.teacher_id=#{teacherId} AND sr.status='PUBLISHED'
            """)
    int publishedRunOwned(@Param("runId") long runId, @Param("teacherId") long teacherId);

    @Update("""
            UPDATE submission s JOIN scoring_run sr ON sr.submission_id=s.id
              JOIN scoring_run source ON source.assignment_id=sr.assignment_id AND source.id=#{runId}
            SET s.is_score_published=1,s.score_publish_time=CURRENT_TIMESTAMP,s.updated_at=CURRENT_TIMESTAMP
            """)
    int markSubmissionPublished(long runId);

    @Insert("""
            INSERT INTO individual_score(submission_id,student_id,score_result_id,teacher_score,contribution_ratio,
              individual_final_score,calculated_by,is_published,publish_time)
            SELECT sr.submission_id,r.student_id,r.id,r.group_score,r.contribution*100,r.final_score,
                   sr.triggered_by_teacher_id,1,CURRENT_TIMESTAMP
            FROM score_result r JOIN scoring_run sr ON sr.id=r.scoring_run_id
            JOIN scoring_run source ON source.assignment_id=sr.assignment_id AND source.id=#{runId}
            WHERE r.status='PUBLISHED'
            ON DUPLICATE KEY UPDATE score_result_id=VALUES(score_result_id),teacher_score=VALUES(teacher_score),
              contribution_ratio=VALUES(contribution_ratio),individual_final_score=VALUES(individual_final_score),
              calculated_by=VALUES(calculated_by),is_published=1,publish_time=CURRENT_TIMESTAMP
            """)
    int projectIndividualScores(long runId);

    @Insert("""
            INSERT INTO final_score(student_id,assignment_id,course_id,score_result_id,type,weight,score,
              graded_time,graded_by,comment,create_time,update_time)
            SELECT r.student_id,sr.assignment_id,a.course_id,r.id,'ASSIGNMENT',1.0000,r.final_score,
                   CURRENT_TIMESTAMP,sr.triggered_by_teacher_id,r.review_reason,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
            FROM score_result r JOIN scoring_run sr ON sr.id=r.scoring_run_id
            JOIN assignment a ON a.id=sr.assignment_id
            JOIN scoring_run source ON source.assignment_id=sr.assignment_id AND source.id=#{runId}
            WHERE r.status='PUBLISHED'
            ON DUPLICATE KEY UPDATE score_result_id=VALUES(score_result_id),course_id=VALUES(course_id),
              score=VALUES(score),graded_time=CURRENT_TIMESTAMP,graded_by=VALUES(graded_by),comment=VALUES(comment),
              update_time=CURRENT_TIMESTAMP
            """)
    int projectFinalScores(long runId);

    @Select("""
            SELECT r.id AS scoreResultId,a.id AS assignmentId,a.title AS assignmentTitle,c.name AS courseName,
                   g.name AS groupName,r.group_score AS groupScore,r.contribution,r.rule_score AS ruleScore,
                   r.model_score AS modelScore,
                   COALESCE((SELECT rev.revised_score FROM score_revision rev
                             WHERE rev.score_result_id=r.id ORDER BY rev.created_at DESC,rev.id DESC LIMIT 1),
                            r.final_score) AS finalScore,
                   r.explanation_json AS explanation,snap.source_snapshot_json AS scoringSnapshot,
                   r.review_reason AS reviewReason,r.published_at AS publishedAt,sr.run_no AS runNo,
                   mv.version AS modelVersion,rule.formula_version AS ruleVersion
            FROM score_result r JOIN scoring_run sr ON sr.id=r.scoring_run_id
            JOIN assignment a ON a.id=sr.assignment_id JOIN course c ON c.id=a.course_id
            JOIN group_table g ON g.id=sr.group_id
            LEFT JOIN model_version mv ON mv.id=sr.model_version_id
            LEFT JOIN scoring_rule rule ON rule.id=sr.scoring_rule_id
            LEFT JOIN scoring_input_snapshot snap ON snap.scoring_run_id=sr.id AND snap.student_id=r.student_id
            WHERE r.student_id=#{studentId} AND r.status='PUBLISHED' ORDER BY r.published_at DESC
            """)
    List<Map<String, Object>> publishedScores(long studentId);

    @Select("""
            SELECT id,student_id AS studentId,status,published_at AS publishedAt
            FROM score_result WHERE id=#{resultId} AND student_id=#{studentId} AND status='PUBLISHED'
            """)
    PublishedScoreRow publishedScore(@Param("resultId") long resultId,
                                     @Param("studentId") long studentId);

    @Select("""
            SELECT COUNT(*) FROM score_appeal
            WHERE score_result_id=#{resultId} AND student_id=#{studentId} AND status IN ('SUBMITTED','PROCESSING')
            """)
    int openAppealCount(@Param("resultId") long resultId, @Param("studentId") long studentId);

    @Insert("""
            INSERT INTO score_appeal(score_result_id,student_id,reason,evidence_json,status,submitted_at,created_at,updated_at)
            VALUES(#{resultId},#{studentId},#{reason},#{evidence},'SUBMITTED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertAppeal(@Param("resultId") long resultId, @Param("studentId") long studentId,
                     @Param("reason") String reason, @Param("evidence") String evidence);

    @Select("""
            SELECT ap.id,ap.score_result_id AS scoreResultId,ap.student_id AS studentId,s.name AS studentName,
                   a.title AS assignmentTitle,ap.reason,ap.evidence_json AS evidence,ap.status,
                   ap.resolution,ap.submitted_at AS submittedAt,ap.resolved_at AS resolvedAt,
                   COALESCE((SELECT rev.revised_score FROM score_revision rev
                             WHERE rev.score_result_id=r.id ORDER BY rev.created_at DESC,rev.id DESC LIMIT 1),
                            r.final_score) AS currentScore
            FROM score_appeal ap JOIN score_result r ON r.id=ap.score_result_id
            JOIN scoring_run sr ON sr.id=r.scoring_run_id JOIN assignment a ON a.id=sr.assignment_id
            JOIN student_users s ON s.id=ap.student_id WHERE a.teacher_id=#{teacherId}
            ORDER BY ap.submitted_at DESC
            """)
    List<Map<String, Object>> teacherAppeals(long teacherId);

    @Select("""
            SELECT ap.id,ap.score_result_id AS scoreResultId,a.title AS assignmentTitle,
                   ap.reason,ap.evidence_json AS evidence,ap.status,ap.resolution,
                   ap.submitted_at AS submittedAt,ap.resolved_at AS resolvedAt,
                   COALESCE((SELECT rev.revised_score FROM score_revision rev
                             WHERE rev.score_result_id=r.id ORDER BY rev.created_at DESC,rev.id DESC LIMIT 1),
                            r.final_score) AS currentScore
            FROM score_appeal ap JOIN score_result r ON r.id=ap.score_result_id
            JOIN scoring_run sr ON sr.id=r.scoring_run_id JOIN assignment a ON a.id=sr.assignment_id
            WHERE ap.student_id=#{studentId} ORDER BY ap.submitted_at DESC
            """)
    List<Map<String, Object>> studentAppeals(long studentId);

    @Update("""
            UPDATE score_appeal ap JOIN score_result r ON r.id=ap.score_result_id
            JOIN scoring_run sr ON sr.id=r.scoring_run_id JOIN assignment a ON a.id=sr.assignment_id
            SET ap.status=#{status},ap.resolution=#{resolution},ap.resolved_by_teacher_id=#{teacherId},
                ap.resolved_at=CURRENT_TIMESTAMP,ap.updated_at=CURRENT_TIMESTAMP
            WHERE ap.id=#{appealId} AND a.teacher_id=#{teacherId} AND ap.status IN ('SUBMITTED','PROCESSING')
            """)
    int resolveAppeal(@Param("appealId") long appealId, @Param("teacherId") long teacherId,
                      @Param("status") String status, @Param("resolution") String resolution);

    @Select("""
            SELECT COALESCE((SELECT rev.revised_score FROM score_revision rev
                             WHERE rev.score_result_id=r.id ORDER BY rev.created_at DESC,rev.id DESC LIMIT 1),
                            r.final_score)
            FROM score_appeal ap JOIN score_result r ON r.id=ap.score_result_id
            JOIN scoring_run sr ON sr.id=r.scoring_run_id JOIN assignment a ON a.id=sr.assignment_id
            WHERE ap.id=#{appealId} AND a.teacher_id=#{teacherId} AND ap.status IN ('SUBMITTED','PROCESSING')
            FOR UPDATE
            """)
    Double effectiveScoreForAppeal(@Param("appealId") long appealId, @Param("teacherId") long teacherId);

    @Insert("""
            INSERT INTO score_revision(score_result_id,score_appeal_id,revised_by_teacher_id,
              previous_score,revised_score,reason,created_at)
            SELECT ap.score_result_id,ap.id,#{teacherId},#{previousScore},#{revisedScore},#{reason},CURRENT_TIMESTAMP
            FROM score_appeal ap JOIN score_result r ON r.id=ap.score_result_id
            JOIN scoring_run sr ON sr.id=r.scoring_run_id JOIN assignment a ON a.id=sr.assignment_id
            WHERE ap.id=#{appealId} AND a.teacher_id=#{teacherId} AND ap.status='ACCEPTED'
            """)
    int insertScoreRevision(@Param("appealId") long appealId, @Param("teacherId") long teacherId,
                            @Param("previousScore") double previousScore,
                            @Param("revisedScore") double revisedScore, @Param("reason") String reason);

    @Update("""
            UPDATE individual_score i JOIN score_appeal ap ON ap.score_result_id=i.score_result_id
            SET i.individual_final_score=#{score},i.calculated_at=CURRENT_TIMESTAMP
            WHERE ap.id=#{appealId}
            """)
    int updateIndividualScoreProjection(@Param("appealId") long appealId, @Param("score") double score);

    @Update("""
            UPDATE final_score f JOIN score_appeal ap ON ap.score_result_id=f.score_result_id
            SET f.score=#{score},f.comment=CONCAT_WS(' | ',NULLIF(f.comment,''),#{reason}),
                f.update_time=CURRENT_TIMESTAMP
            WHERE ap.id=#{appealId}
            """)
    int updateFinalScoreProjection(@Param("appealId") long appealId, @Param("score") double score,
                                   @Param("reason") String reason);
}
