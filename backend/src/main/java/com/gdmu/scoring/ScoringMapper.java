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

    @Select("""
            SELECT s.id AS submissionId,a.id AS assignmentId,s.group_id AS groupId,a.teacher_id AS teacherId,
                   a.standard_group_size AS standardGroupSize,
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

    @Select("SELECT id FROM assignment WHERE id=#{assignmentId} FOR UPDATE")
    Long lockAssignmentForWorkload(long assignmentId);

    @Select("""
            SELECT COUNT(*) FROM submission
            WHERE assignment_id=#{assignmentId} AND group_id=#{groupId} AND is_latest=1
              AND (status='GRADED' OR is_score_published=1)
            """)
    int gradedGroupSubmission(@Param("assignmentId") long assignmentId, @Param("groupId") long groupId);

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
              AND team_status NOT IN ('DISSOLVED','ARCHIVED')
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
              AND status IN ('DRAFT','PENDING_CONFIRMATION','LOCKED')
            """)
    int supersedeOpenWorkloads(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId);

    @Insert("""
            INSERT INTO workload_set(group_id,assignment_id,version_no,status,created_by_teacher_id,
              submitted_at,locked_at,created_at,updated_at)
            VALUES(#{groupId},#{assignmentId},#{version},'LOCKED',#{teacherId},CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertWorkloadSet(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId,
                          @Param("version") int version, @Param("teacherId") long teacherId);

    @Insert("""
            INSERT INTO workload_set(group_id,assignment_id,version_no,status,created_by_student_id,
              submitted_at,locked_at,created_at,updated_at)
            VALUES(#{groupId},#{assignmentId},#{version},'LOCKED',#{studentId},CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
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
                   ws.locked_at AS lockedAt,
                   wc.status AS myConfirmation,wc.comment,
                   (a.deadline IS NOT NULL AND ws.locked_at IS NOT NULL
                     AND ws.locked_at >= a.deadline) AS autoLocked
            FROM workload_set ws
            JOIN assignment a ON a.id=ws.assignment_id
            JOIN group_member gm ON gm.group_id=ws.group_id AND gm.student_id=#{studentId}
              AND gm.status='ACTIVE'
            LEFT JOIN workload_confirmation wc ON wc.workload_set_id=ws.id AND wc.student_id=#{studentId}
            WHERE ws.assignment_id=#{assignmentId} AND ws.status<>'SUPERSEDED'
            ORDER BY ws.version_no DESC LIMIT 1
            """)
    Map<String, Object> studentWorkload(@Param("assignmentId") long assignmentId,
                                        @Param("studentId") long studentId);

    @Select("""
            SELECT id FROM workload_set
            WHERE assignment_id=#{assignmentId} AND group_id=#{groupId} AND status<>'SUPERSEDED'
            ORDER BY version_no DESC LIMIT 1
            """)
    Long latestWorkloadSetId(@Param("assignmentId") long assignmentId, @Param("groupId") long groupId);

    @Select("""
            SELECT ws.id,ws.version_no AS versionNo,ws.status,ws.submitted_at AS submittedAt,ws.locked_at AS lockedAt,
                   (a.deadline IS NOT NULL AND ws.locked_at IS NOT NULL
                     AND ws.locked_at >= a.deadline) AS autoLocked
            FROM workload_set ws JOIN assignment a ON a.id=ws.assignment_id
            WHERE ws.assignment_id=#{assignmentId} AND ws.group_id=#{groupId} AND a.teacher_id=#{teacherId}
              AND ws.status<>'SUPERSEDED'
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

    @Select("SELECT COUNT(*) FROM workload_confirmation WHERE workload_set_id=#{setId} AND status='OBJECTED'")
    int objectedCount(long setId);

    @Select("""
            SELECT wc.student_id AS studentId,s.name AS studentName,wc.comment,wc.confirmed_at AS confirmedAt
            FROM workload_confirmation wc JOIN student_users s ON s.id=wc.student_id
            WHERE wc.workload_set_id=#{setId} AND wc.status='OBJECTED'
            ORDER BY wc.confirmed_at,wc.student_id
            """)
    List<Map<String, Object>> workloadObjections(long setId);

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

    // ---- Deadline auto-lock -------------------------------------------------
    // At the deadline the latest still-open workload version of each group is frozen even when
    // some members never confirmed. Scoring always reads a LOCKED version afterwards.

    @Select("SELECT COUNT(*) FROM assignment WHERE id=#{assignmentId} AND (deadline IS NULL OR deadline <= CURRENT_TIMESTAMP)")
    int expiredAssignmentCount(long assignmentId);

    @Select("""
            SELECT COUNT(*) FROM workload_set ws JOIN assignment a ON a.id=ws.assignment_id
            WHERE ws.id=#{setId} AND (a.deadline IS NULL OR a.deadline <= CURRENT_TIMESTAMP)
            """)
    int expiredWorkloadSetCount(long setId);

    @Select("""
            SELECT ws.id FROM workload_set ws JOIN assignment a ON a.id=ws.assignment_id
            WHERE ws.assignment_id=#{assignmentId} AND a.deadline IS NOT NULL
              AND a.deadline <= CURRENT_TIMESTAMP
              AND ws.status IN ('DRAFT','PENDING_CONFIRMATION','CONFIRMED')
              AND ws.version_no=(SELECT MAX(w2.version_no) FROM workload_set w2
                                 WHERE w2.group_id=ws.group_id AND w2.assignment_id=ws.assignment_id)
            ORDER BY ws.id
            """)
    List<Long> expiredOpenWorkloadSetIds(long assignmentId);

    @Update("""
            UPDATE workload_set old_set JOIN workload_set current_set
              ON current_set.id=#{setId} AND old_set.group_id=current_set.group_id
              AND old_set.assignment_id=current_set.assignment_id
            SET old_set.status='SUPERSEDED',old_set.updated_at=CURRENT_TIMESTAMP
            WHERE old_set.version_no<current_set.version_no
              AND old_set.status IN ('DRAFT','PENDING_CONFIRMATION','CONFIRMED','LOCKED')
            """)
    int supersedeOtherWorkloadSets(long setId);

    @Update("""
            UPDATE workload_set SET status='LOCKED',locked_at=COALESCE(locked_at,CURRENT_TIMESTAMP),
              updated_at=CURRENT_TIMESTAMP
            WHERE id=#{setId} AND status IN ('DRAFT','PENDING_CONFIRMATION','CONFIRMED')
            """)
    int autoLockWorkloadSet(long setId);

    @Select("""
            SELECT DISTINCT ws.assignment_id FROM workload_set ws
            JOIN assignment a ON a.id=ws.assignment_id
            WHERE a.deadline IS NOT NULL AND a.deadline <= CURRENT_TIMESTAMP
              AND ws.status IN ('DRAFT','PENDING_CONFIRMATION','CONFIRMED')
              AND ws.version_no=(SELECT MAX(w2.version_no) FROM workload_set w2
                                 WHERE w2.group_id=ws.group_id AND w2.assignment_id=ws.assignment_id)
            LIMIT 200
            """)
    List<Long> assignmentsWithExpiredOpenWorkloads();

    @Select("""
            SELECT mv.id,mv.version,mv.artifact_sha256 AS artifactSha256 FROM model_version mv
            WHERE mv.status='ACTIVE' AND mv.algorithm='XGBOOST' AND mv.model_key='smoothstep-score'
            ORDER BY mv.activated_at DESC LIMIT 1
            """)
    ActiveModelRow activeModel();

    @Insert("""
            INSERT INTO scoring_run(run_no,assignment_id,group_id,submission_id,scoring_rule_id,model_version_id,
              status,standard_group_size,group_score,input_hash,triggered_by_teacher_id,
              started_at,created_at,updated_at)
            VALUES(#{runNo},#{c.assignmentId},#{c.groupId},#{c.submissionId},#{c.scoringRuleId},#{modelId},
              'CREATED',#{c.standardGroupSize},#{c.groupScore},#{inputHash},#{c.teacherId},
              CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertRun(@Param("runNo") String runNo, @Param("c") SubmissionContext context,
                  @Param("modelId") Long modelId, @Param("inputHash") String inputHash);

    @Select("SELECT id FROM scoring_run WHERE run_no=#{runNo}")
    long runId(String runNo);

    @Insert("""
            INSERT INTO scoring_input_snapshot(scoring_run_id,student_id,contribution,standard_group_size,
              group_score,feature_schema_version,features_json,source_snapshot_json,feature_hash,created_at)
            VALUES(#{runId},#{studentId},#{contribution},#{standardGroupSize},#{groupScore},
              'smoothstep-v1',CAST(#{featuresJson} AS JSON),CAST(#{sourceJson} AS JSON),#{featureHash},CURRENT_TIMESTAMP)
            """)
    int insertSnapshot(@Param("runId") long runId, @Param("studentId") long studentId,
                       @Param("contribution") double contribution,
                       @Param("standardGroupSize") int standardGroupSize,
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
            SELECT sr.status FROM scoring_run sr JOIN assignment a ON a.id=sr.assignment_id
            WHERE sr.id=#{runId} AND a.teacher_id=#{teacherId}
            FOR UPDATE
            """)
    String lockTeacherRunStatus(@Param("runId") long runId, @Param("teacherId") long teacherId);

    @Select("""
            SELECT r.id,r.student_id AS studentId,s.name AS studentName,r.contribution,r.group_score AS groupScore,
                   r.rule_score AS ruleScore,r.model_score AS modelScore,r.model_rule_delta AS modelRuleDelta,
                   r.anomaly_flag AS anomaly,r.final_score AS finalScore,r.status,r.explanation_json AS explanation,
                   r.review_reason AS reviewReason
            FROM score_result r JOIN student_users s ON s.id=r.student_id
            WHERE r.scoring_run_id=#{runId} ORDER BY s.student_user_id
            """)
    List<Map<String, Object>> runResults(long runId);

    @Select("SELECT COUNT(*) FROM score_result WHERE scoring_run_id=#{runId} AND status != 'REVIEWED'")
    int unreviewedResultCount(long runId);

    /**
     * 逐人复核流程已取消：发布前把本次运行的草稿成绩统一标为 REVIEWED，
     * 最终分保持算法值（manual_adjustment=0），并把审计原因写进 review_reason。
     */
    @Update("""
            UPDATE score_result r JOIN scoring_run sr ON sr.id=r.scoring_run_id JOIN assignment a ON a.id=sr.assignment_id
            SET r.manual_adjustment=0,r.review_reason=#{reason},r.reviewed_by_teacher_id=#{teacherId},
                r.reviewed_at=CURRENT_TIMESTAMP,r.status='REVIEWED',r.updated_at=CURRENT_TIMESTAMP
            WHERE sr.id=#{runId} AND a.teacher_id=#{teacherId}
              AND sr.status IN ('MODEL_COMPLETE','DEGRADED') AND r.status='DRAFT'
            """)
    int reviewAllResults(@Param("runId") long runId, @Param("teacherId") long teacherId,
                         @Param("reason") String reason);

    @Update("""
            UPDATE scoring_run sr JOIN assignment a ON a.id=sr.assignment_id
            SET sr.status='APPROVED',sr.updated_at=CURRENT_TIMESTAMP
            WHERE sr.id=#{runId} AND a.teacher_id=#{teacherId} AND sr.status IN ('MODEL_COMPLETE','DEGRADED')
            """)
    int approveRun(@Param("runId") long runId, @Param("teacherId") long teacherId);

    @Update("""
            UPDATE score_result r JOIN scoring_run sr ON sr.id=r.scoring_run_id
            SET r.status='PUBLISHED',r.published_at=CURRENT_TIMESTAMP,r.updated_at=CURRENT_TIMESTAMP
            WHERE sr.id=#{runId} AND sr.status='PUBLISHED' AND r.status='REVIEWED'
            """)
    int publishResults(long runId);

    @Update("""
            UPDATE scoring_run sr JOIN assignment a ON a.id=sr.assignment_id
            SET sr.status='PUBLISHED',sr.updated_at=CURRENT_TIMESTAMP
            WHERE sr.id=#{runId} AND a.teacher_id=#{teacherId} AND sr.status='APPROVED'
            """)
    int publishRun(@Param("runId") long runId, @Param("teacherId") long teacherId);

    @Update("""
            UPDATE submission s JOIN scoring_run sr ON sr.submission_id=s.id
            SET s.is_score_published=1,s.score_publish_time=CURRENT_TIMESTAMP,s.updated_at=CURRENT_TIMESTAMP
            WHERE sr.id=#{runId} AND sr.status='PUBLISHED'
            """)
    int markSubmissionPublished(long runId);

    @Insert("""
            INSERT INTO individual_score(submission_id,student_id,score_result_id,teacher_score,contribution_ratio,
              individual_final_score,calculated_by,is_published,publish_time)
            SELECT sr.submission_id,r.student_id,r.id,r.group_score,r.contribution*100,r.final_score,
                   sr.triggered_by_teacher_id,1,CURRENT_TIMESTAMP
            FROM score_result r JOIN scoring_run sr ON sr.id=r.scoring_run_id
            WHERE sr.id=#{runId} AND sr.status='PUBLISHED' AND r.status='PUBLISHED'
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
            WHERE sr.id=#{runId} AND sr.status='PUBLISHED' AND r.status='PUBLISHED'
            ON DUPLICATE KEY UPDATE score_result_id=VALUES(score_result_id),course_id=VALUES(course_id),
              score=VALUES(score),graded_time=CURRENT_TIMESTAMP,graded_by=VALUES(graded_by),comment=VALUES(comment),
              update_time=CURRENT_TIMESTAMP
            """)
    int projectFinalScores(long runId);

    @Select("""
            SELECT r.id AS scoreResultId,r.student_id AS studentId,a.id AS assignmentId,a.title AS assignmentTitle,c.id AS courseId,c.name AS courseName,
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
            WHERE r.student_id=#{studentId} AND r.status='PUBLISHED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM score_result newer
                  JOIN scoring_run newer_run ON newer_run.id=newer.scoring_run_id
                  WHERE newer.student_id=r.student_id
                    AND newer.status='PUBLISHED'
                    AND newer_run.assignment_id=sr.assignment_id
                    AND (newer.published_at>r.published_at
                         OR (newer.published_at=r.published_at AND newer.id>r.id))
              )
            ORDER BY r.published_at DESC
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
                   a.course_id AS courseId,a.title AS assignmentTitle,ap.reason,ap.evidence_json AS evidence,ap.status,
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
