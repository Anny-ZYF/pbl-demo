package com.gdmu.admin;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface AdminMapper {
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM student_users) AS studentCount,
              (SELECT COUNT(*) FROM teacher_users) AS teacherCount,
              (SELECT COUNT(*) FROM course WHERE status = 'ACTIVE') AS activeCourseCount,
              (SELECT COUNT(*) FROM assignment WHERE status IN ('PUBLISHED','CLOSED')) AS assignmentCount,
              (SELECT COUNT(*) FROM model_version WHERE status = 'ACTIVE') AS activeModelCount,
              (SELECT COUNT(*) FROM scoring_run) AS scoringRunCount,
              (SELECT version FROM model_version WHERE status='ACTIVE' ORDER BY activated_at DESC LIMIT 1) AS activeModelVersion,
              (SELECT COUNT(*) FROM scoring_run WHERE status = 'DEGRADED') AS degradedRunCount,
              (SELECT COUNT(*) FROM score_result WHERE anomaly_flag = 1 AND anomaly_status = 'PENDING') AS pendingAnomalyCount,
              (SELECT COUNT(*) FROM score_appeal WHERE status IN ('SUBMITTED','PROCESSING')) AS openAppealCount
            """)
    Map<String, Object> dashboard();

    @Select("""
            SELECT sr.id,sr.run_no AS runNo,sr.status,a.title AS assignmentTitle,mv.version AS modelVersion,
                   sr.completed_at AS finishedAt,sr.updated_at AS updatedAt
            FROM scoring_run sr JOIN assignment a ON a.id=sr.assignment_id
            LEFT JOIN model_version mv ON mv.id=sr.model_version_id
            ORDER BY sr.created_at DESC LIMIT 5
            """)
    List<Map<String, Object>> recentScoringRuns();

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM department WHERE status = 'ACTIVE') AS departmentCount,
              (SELECT COUNT(*) FROM major WHERE status = 'ACTIVE') AS majorCount,
              (SELECT COUNT(*) FROM class WHERE status = 'ACTIVE') AS classCount,
              (SELECT COUNT(*) FROM teacher_users WHERE status = 1) AS activeTeacherCount,
              (SELECT COUNT(*) FROM student_users WHERE status = 1) AS activeStudentCount
            """)
    Map<String, Object> organizationOverview();

    @Select("""
            SELECT id, name, status FROM department ORDER BY name
            """)
    List<Map<String, Object>> departments();

    @Select("SELECT id,department_id AS departmentId,name,status FROM major ORDER BY department_id,name")
    List<Map<String, Object>> majors();

    @Select("SELECT id,major_id AS majorId,name,grade,status FROM `class` ORDER BY major_id,grade,name")
    List<Map<String, Object>> classes();

    @Select("""
            <script>
            SELECT v.id, v.username, v.display_name AS displayName, v.display_name AS name, v.role, v.status,
                   v.must_change_password AS mustChangePassword,
                   COALESCE(t.department_id,m.department_id) AS departmentId,
                   stu.major_id AS majorId,stu.class_id AS classId,stu.grade,
                   d.name AS departmentName,ma.name AS majorName,cl.name AS className,
                   CASE WHEN v.role='TEACHER' THEN d.name
                        WHEN v.role='STUDENT' THEN COALESCE(cl.name,ma.name,d.name) ELSE '系统管理' END AS organizationName
            FROM user_account_view v
            LEFT JOIN teacher_users t ON v.role='TEACHER' AND t.id=v.id
            LEFT JOIN student_users stu ON v.role='STUDENT' AND stu.id=v.id
            LEFT JOIN major m ON m.id=stu.major_id
            LEFT JOIN department d ON d.id=COALESCE(t.department_id,m.department_id)
            LEFT JOIN major ma ON ma.id=stu.major_id
            LEFT JOIN `class` cl ON cl.id=stu.class_id
            WHERE 1=1
            <if test='role != null and role != ""'>AND v.role = #{role}</if>
            <if test='status != null'>AND v.status = #{status}</if>
            <if test='keyword != null and keyword != ""'>
              AND (v.username LIKE CONCAT('%', #{keyword}, '%') OR v.display_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY v.role, v.username
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> accounts(@Param("role") String role, @Param("status") Integer status,
                                       @Param("keyword") String keyword, @Param("offset") int offset,
                                       @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM user_account_view
            WHERE 1=1
            <if test='role != null and role != ""'>AND role = #{role}</if>
            <if test='status != null'>AND status = #{status}</if>
            <if test='keyword != null and keyword != ""'>
              AND (username LIKE CONCAT('%', #{keyword}, '%') OR display_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long accountCount(@Param("role") String role, @Param("status") Integer status,
                      @Param("keyword") String keyword);

    @Insert("""
            INSERT INTO admin_users(username,password,display_name,status,role,must_change_password,create_time,update_time)
            VALUES(#{username},#{passwordHash},#{name},1,'ROLE_ADMIN',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertAdmin(@Param("username") String username, @Param("passwordHash") String passwordHash,
                    @Param("name") String name);

    @Insert("""
            INSERT INTO teacher_users(teacher_user_id,password,name,department_id,status,role,must_change_password,create_time,update_time)
            VALUES(#{username},#{passwordHash},#{name},#{departmentId},1,'ROLE_TEACHER',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertTeacher(@Param("username") String username, @Param("passwordHash") String passwordHash,
                      @Param("name") String name, @Param("departmentId") Long departmentId);

    @Insert("""
            INSERT INTO student_users(student_user_id,password,name,major_id,class_id,grade,status,role,must_change_password,create_time,update_time)
            VALUES(#{username},#{passwordHash},#{name},#{majorId},#{classId},#{grade},1,'ROLE_STUDENT',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertStudent(@Param("username") String username, @Param("passwordHash") String passwordHash,
                      @Param("name") String name, @Param("majorId") Long majorId,
                      @Param("classId") Long classId, @Param("grade") Integer grade);

    @Update("UPDATE admin_users SET display_name=#{name}, update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateAdmin(@Param("id") long id, @Param("name") String name);

    @Update("""
            UPDATE teacher_users SET name=#{name}, department_id=#{departmentId}, update_time=CURRENT_TIMESTAMP WHERE id=#{id}
            """)
    int updateTeacher(@Param("id") long id, @Param("name") String name, @Param("departmentId") Long departmentId);

    @Update("""
            UPDATE student_users SET name=#{name}, major_id=#{majorId}, class_id=#{classId}, grade=#{grade},
                                     update_time=CURRENT_TIMESTAMP WHERE id=#{id}
            """)
    int updateStudent(@Param("id") long id, @Param("name") String name, @Param("majorId") Long majorId,
                      @Param("classId") Long classId, @Param("grade") Integer grade);

    @Update("UPDATE admin_users SET status=#{status},token_version=token_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int setAdminStatus(@Param("id") long id, @Param("status") int status);
    @Update("UPDATE teacher_users SET status=#{status},token_version=token_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int setTeacherStatus(@Param("id") long id, @Param("status") int status);
    @Update("UPDATE student_users SET status=#{status},token_version=token_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int setStudentStatus(@Param("id") long id, @Param("status") int status);

    @Update("UPDATE admin_users SET password=#{hash},must_change_password=1,token_version=token_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int resetAdminPassword(@Param("id") long id, @Param("hash") String hash);
    @Update("UPDATE teacher_users SET password=#{hash},must_change_password=1,token_version=token_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int resetTeacherPassword(@Param("id") long id, @Param("hash") String hash);
    @Update("UPDATE student_users SET password=#{hash},must_change_password=1,token_version=token_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int resetStudentPassword(@Param("id") long id, @Param("hash") String hash);

    @Select("""
            <script>
            SELECT c.id, c.code, c.name, c.term, c.grade, c.status,
                   t.name AS teacherName, d.name AS departmentName,
                   c.begin_date AS beginDate, c.end_date AS endDate,
                   (SELECT COUNT(*) FROM student_course sc WHERE sc.course_id=c.id AND sc.status='ACTIVE') AS studentCount,
                   (SELECT COUNT(*) FROM assignment a WHERE a.course_id=c.id) AS assignmentCount,
                   (SELECT COUNT(*) FROM group_table g JOIN assignment a ON a.id=g.assignment_id
                     WHERE a.course_id=c.id AND g.team_status NOT IN ('DISSOLVED','ARCHIVED')) AS groupCount
            FROM course c LEFT JOIN teacher_users t ON t.id=c.teacher_id
            LEFT JOIN major m ON m.id=c.major_id LEFT JOIN department d ON d.id=m.department_id
            WHERE 1=1
            <if test='keyword != null and keyword != ""'>
              AND (c.name LIKE CONCAT('%',#{keyword},'%') OR c.code LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test='teacherName != null and teacherName != ""'>
              AND t.name LIKE CONCAT('%',#{teacherName},'%')
            </if>
            <if test='status != null and status != ""'>AND c.status=#{status}</if>
            ORDER BY c.id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> courses(@Param("keyword") String keyword, @Param("teacherName") String teacherName,
                                      @Param("status") String status,
                                      @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM course c LEFT JOIN teacher_users t ON t.id=c.teacher_id WHERE 1=1
            <if test='keyword != null and keyword != ""'>
              AND (c.name LIKE CONCAT('%',#{keyword},'%') OR c.code LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test='teacherName != null and teacherName != ""'>
              AND t.name LIKE CONCAT('%',#{teacherName},'%')
            </if>
            <if test='status != null and status != ""'>AND c.status=#{status}</if>
            </script>
            """)
    long courseCount(@Param("keyword") String keyword, @Param("teacherName") String teacherName,
                     @Param("status") String status);

    @Select("""
            <script>
            SELECT id, model_key AS modelKey, model_key AS name, version, algorithm, status, artifact_uri AS artifactUri,
                   artifact_sha256 AS artifactSha256, metrics_json AS metricsJson,
                   trained_at AS trainedAt, activated_at AS activatedAt, created_at AS createdAt
            FROM model_version WHERE 1=1
            <if test='status == "ACTIVE"'>AND status='ACTIVE'</if>
            <if test='status == "INACTIVE"'>AND status!='ACTIVE'</if>
            ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> modelVersions(@Param("status") String status,
                                            @Param("offset") int offset, @Param("size") int size);
    @Select("""
            <script>SELECT COUNT(*) FROM model_version WHERE 1=1
            <if test='status == "ACTIVE"'>AND status='ACTIVE'</if>
            <if test='status == "INACTIVE"'>AND status!='ACTIVE'</if>
            </script>
            """)
    long modelVersionCount(@Param("status") String status);

    @Insert("""
            INSERT INTO model_version(model_key,version,algorithm,status,artifact_uri,artifact_sha256,
              feature_schema_json,hyperparameters_json,metrics_json,created_by_admin_id,created_at,updated_at)
            VALUES(#{r.modelKey},#{r.version},'XGBOOST','VALIDATED',#{r.artifactUri},#{r.artifactSha256},
              CAST(#{r.featureSchemaJson} AS JSON),CAST(#{r.hyperparametersJson} AS JSON),CAST(#{r.metricsJson} AS JSON),
              #{adminId},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertModel(@Param("r") ModelRegistrationRequest request, @Param("adminId") long adminId);

    @Select("SELECT id FROM model_version WHERE model_key=#{modelKey} AND version=#{version}")
    Long modelId(@Param("modelKey") String modelKey, @Param("version") String version);

    @Select("""
            SELECT model_key AS modelKey,status,algorithm,artifact_uri AS artifactUri,
                   artifact_sha256 AS artifactSha256,feature_schema_json AS featureSchemaJson,
                   metrics_json AS metricsJson
            FROM model_version WHERE id=#{id}
            """)
    Map<String, Object> modelForActivation(long id);

    @Update("UPDATE model_version SET status='RETIRED', updated_at=CURRENT_TIMESTAMP WHERE model_key=#{modelKey} AND status='ACTIVE'")
    int retireActiveModel(String modelKey);

    @Update("UPDATE model_version SET status='ACTIVE', activated_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND status IN ('VALIDATED','RETIRED')")
    int activateModel(long id);

    @Select("""
            <script>
            SELECT sr.id, sr.run_no AS runNo, sr.status, sr.assignment_id AS assignmentId,
                   a.title AS assignmentTitle, g.name AS groupName, sr.started_at AS startedAt,
                   sr.completed_at AS completedAt, sr.completed_at AS finishedAt, sr.error_message AS errorMessage,
                   mv.version AS modelVersion,
                   (SELECT COUNT(*) FROM score_result x WHERE x.scoring_run_id=sr.id) AS sampleCount
            FROM scoring_run sr
            JOIN assignment a ON a.id=sr.assignment_id
            LEFT JOIN group_table g ON g.id=sr.group_id
            LEFT JOIN model_version mv ON mv.id=sr.model_version_id
            WHERE 1=1
            <if test='keyword != null and keyword != ""'>
              AND (sr.run_no LIKE CONCAT('%',#{keyword},'%') OR a.title LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test='status == "RUNNING"'>AND sr.status='CREATED'</if>
            <if test='status == "SUCCESS"'>AND sr.status IN ('MODEL_COMPLETE','APPROVED','PUBLISHED')</if>
            <if test='status == "FAILED"'>AND sr.status IN ('FAILED','DEGRADED')</if>
            ORDER BY sr.created_at DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> scoringRuns(@Param("keyword") String keyword, @Param("status") String status,
                                          @Param("offset") int offset, @Param("size") int size);
    @Select("""
            <script>
            SELECT COUNT(*) FROM scoring_run sr JOIN assignment a ON a.id=sr.assignment_id WHERE 1=1
            <if test='keyword != null and keyword != ""'>
              AND (sr.run_no LIKE CONCAT('%',#{keyword},'%') OR a.title LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test='status == "RUNNING"'>AND sr.status='CREATED'</if>
            <if test='status == "SUCCESS"'>AND sr.status IN ('MODEL_COMPLETE','APPROVED','PUBLISHED')</if>
            <if test='status == "FAILED"'>AND sr.status IN ('FAILED','DEGRADED')</if>
            </script>
            """)
    long scoringRunCount(@Param("keyword") String keyword, @Param("status") String status);

    @Select("""
            <script>
            SELECT r.id, r.student_id AS studentId, s.name AS studentName, s.name AS subjectName,
                   a.title AS assignmentTitle,
                   r.group_score AS groupScore, r.rule_score AS ruleScore, r.model_score AS modelScore,
                   r.model_rule_delta AS modelRuleDelta,
                   r.anomaly_status AS status,
                   CASE WHEN r.model_rule_delta &gt; 5 THEN 'HIGH'
                        WHEN r.model_rule_delta &gt; 3 THEN 'MEDIUM' ELSE 'LOW' END AS severity,
                   'MODEL_RULE_DEVIATION' AS type,r.review_reason AS reviewReason,
                   r.anomaly_resolution AS resolution,r.anomaly_resolved_at AS resolvedAt,
                   run.run_no AS runNo,r.created_at AS createdAt,r.created_at AS detectedAt
            FROM score_result r JOIN scoring_run run ON run.id=r.scoring_run_id
            JOIN assignment a ON a.id=run.assignment_id JOIN student_users s ON s.id=r.student_id
            WHERE r.anomaly_flag=1
            <if test='keyword != null and keyword != ""'>
              AND (a.title LIKE CONCAT('%',#{keyword},'%') OR s.name LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test='status == "PENDING"'>AND r.anomaly_status='PENDING'</if>
            <if test='status == "RESOLVED"'>AND r.anomaly_status='RESOLVED'</if>
            <if test='severity == "HIGH"'>AND r.model_rule_delta &gt; 5</if>
            <if test='severity == "MEDIUM"'>AND r.model_rule_delta &gt; 3 AND r.model_rule_delta &lt;= 5</if>
            <if test='severity == "LOW"'>AND r.model_rule_delta &lt;= 3</if>
            ORDER BY r.created_at DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> anomalies(@Param("keyword") String keyword, @Param("severity") String severity,
                                        @Param("status") String status,
                                        @Param("offset") int offset, @Param("size") int size);
    @Select("""
            <script>
            SELECT COUNT(*) FROM score_result r JOIN scoring_run run ON run.id=r.scoring_run_id
            JOIN assignment a ON a.id=run.assignment_id JOIN student_users s ON s.id=r.student_id
            WHERE r.anomaly_flag=1
            <if test='keyword != null and keyword != ""'>
              AND (a.title LIKE CONCAT('%',#{keyword},'%') OR s.name LIKE CONCAT('%',#{keyword},'%'))
            </if>
            <if test='status == "PENDING"'>AND r.anomaly_status='PENDING'</if>
            <if test='status == "RESOLVED"'>AND r.anomaly_status='RESOLVED'</if>
            <if test='severity == "HIGH"'>AND r.model_rule_delta &gt; 5</if>
            <if test='severity == "MEDIUM"'>AND r.model_rule_delta &gt; 3 AND r.model_rule_delta &lt;= 5</if>
            <if test='severity == "LOW"'>AND r.model_rule_delta &lt;= 3</if>
            </script>
            """)
    long anomalyCount(@Param("keyword") String keyword, @Param("severity") String severity,
                      @Param("status") String status);

    @Update("""
            UPDATE score_result SET anomaly_status='RESOLVED',anomaly_resolution=#{resolution},
              anomaly_resolved_by_admin_id=#{adminId},anomaly_resolved_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
            WHERE id=#{id} AND anomaly_flag=1 AND anomaly_status='PENDING'
            """)
    int resolveAnomaly(@Param("id") long id, @Param("adminId") long adminId,
                       @Param("resolution") String resolution);

    @Select("""
            <script>
            SELECT e.id,e.event_type AS eventType,e.actor_role AS actorRole,e.actor_id AS actorId,
                   COALESCE(v.display_name,CONCAT(e.actor_role,' #',e.actor_id)) AS actorName,
                   e.aggregate_type AS aggregateType,e.aggregate_type AS resourceType,
                   e.aggregate_id AS aggregateId,
                   CONCAT(e.aggregate_type,' #',e.aggregate_id) AS resourceName,
                   e.request_id AS requestId,e.detail_json AS detailJson,e.detail_json AS context,
                   e.ip_address AS ipAddress,e.user_agent AS userAgent,'SUCCESS' AS result,
                   e.created_at AS createdAt,e.created_at AS occurredAt
            FROM audit_event e
            LEFT JOIN user_account_view v ON v.id=e.actor_id AND v.role=e.actor_role
            WHERE 1=1
            <if test='eventType != null and eventType != ""'>
              <choose>
                <when test='eventType == "LOGIN"'>AND e.event_type LIKE 'LOGIN%'</when>
                <when test='eventType == "CREATE"'>AND (e.event_type LIKE '%_CREATED' OR e.event_type='MODEL_REGISTERED')</when>
                <when test='eventType == "UPDATE"'>AND (e.event_type LIKE '%_UPDATED' OR e.event_type LIKE '%_CHANGED' OR e.event_type LIKE '%_RESET')</when>
                <when test='eventType == "DELETE"'>AND (e.event_type LIKE '%_DELETED' OR e.event_type LIKE '%_REMOVED')</when>
                <when test='eventType == "SCORING"'>AND (e.event_type LIKE 'SCORING_%' OR e.event_type IN ('GROUP_SCORE_RECORDED','SCORES_PUBLISHED','SCORE_APPEAL_SUBMITTED','SCORE_APPEAL_RESOLVED'))</when>
                <when test='eventType == "ANOMALY_RESOLUTION"'>AND e.event_type='SCORING_ANOMALY_REVIEWED'</when>
                <otherwise>AND e.event_type=#{eventType}</otherwise>
              </choose>
            </if>
            <if test='actorRole != null and actorRole != ""'>AND e.actor_role=#{actorRole}</if>
            <if test='startDate != null and startDate != ""'>AND e.created_at &gt;= CONCAT(#{startDate},' 00:00:00')</if>
            <if test='endDate != null and endDate != ""'>AND e.created_at &lt; DATE_ADD(#{endDate},INTERVAL 1 DAY)</if>
            <if test='keyword != null and keyword != ""'>
              AND (e.event_type LIKE CONCAT('%',#{keyword},'%') OR e.aggregate_type LIKE CONCAT('%',#{keyword},'%')
                   OR e.aggregate_id LIKE CONCAT('%',#{keyword},'%') OR e.detail_json LIKE CONCAT('%',#{keyword},'%')
                   OR v.display_name LIKE CONCAT('%',#{keyword},'%'))
            </if>
            ORDER BY e.created_at DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> auditEvents(@Param("eventType") String eventType,
                                         @Param("keyword") String keyword,
                                         @Param("actorRole") String actorRole,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate,
                                         @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM audit_event e
            LEFT JOIN user_account_view v ON v.id=e.actor_id AND v.role=e.actor_role
            WHERE 1=1
            <if test='eventType != null and eventType != ""'>
              <choose>
                <when test='eventType == "LOGIN"'>AND e.event_type LIKE 'LOGIN%'</when>
                <when test='eventType == "CREATE"'>AND (e.event_type LIKE '%_CREATED' OR e.event_type='MODEL_REGISTERED')</when>
                <when test='eventType == "UPDATE"'>AND (e.event_type LIKE '%_UPDATED' OR e.event_type LIKE '%_CHANGED' OR e.event_type LIKE '%_RESET')</when>
                <when test='eventType == "DELETE"'>AND (e.event_type LIKE '%_DELETED' OR e.event_type LIKE '%_REMOVED')</when>
                <when test='eventType == "SCORING"'>AND (e.event_type LIKE 'SCORING_%' OR e.event_type IN ('GROUP_SCORE_RECORDED','SCORES_PUBLISHED','SCORE_APPEAL_SUBMITTED','SCORE_APPEAL_RESOLVED'))</when>
                <when test='eventType == "ANOMALY_RESOLUTION"'>AND e.event_type='SCORING_ANOMALY_REVIEWED'</when>
                <otherwise>AND e.event_type=#{eventType}</otherwise>
              </choose>
            </if>
            <if test='actorRole != null and actorRole != ""'>AND e.actor_role=#{actorRole}</if>
            <if test='startDate != null and startDate != ""'>AND e.created_at &gt;= CONCAT(#{startDate},' 00:00:00')</if>
            <if test='endDate != null and endDate != ""'>AND e.created_at &lt; DATE_ADD(#{endDate},INTERVAL 1 DAY)</if>
            <if test='keyword != null and keyword != ""'>
              AND (e.event_type LIKE CONCAT('%',#{keyword},'%') OR e.aggregate_type LIKE CONCAT('%',#{keyword},'%')
                   OR e.aggregate_id LIKE CONCAT('%',#{keyword},'%') OR e.detail_json LIKE CONCAT('%',#{keyword},'%')
                   OR v.display_name LIKE CONCAT('%',#{keyword},'%'))
            </if>
            </script>
            """)
    long auditEventCount(@Param("eventType") String eventType, @Param("keyword") String keyword,
                         @Param("actorRole") String actorRole, @Param("startDate") String startDate,
                         @Param("endDate") String endDate);

    @Insert("""
            INSERT INTO import_batch(batch_no,import_type,file_name,file_sha256,status,created_by_admin_id,
              create_time,created_at)
            VALUES(#{batchNo},'ACCOUNT_CSV',#{fileName},#{sha256},'RUNNING',#{adminId},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertImportBatch(@Param("batchNo") String batchNo, @Param("fileName") String fileName,
                          @Param("sha256") String sha256, @Param("adminId") long adminId);

    @Update("""
            UPDATE import_batch SET status=#{status},total_rows=#{total},success_rows=#{success},
              failure_rows=#{failure},error_report_json=CAST(#{errorsJson} AS JSON),completed_at=CURRENT_TIMESTAMP
            WHERE batch_no=#{batchNo}
            """)
    int completeImportBatch(@Param("batchNo") String batchNo, @Param("status") String status,
                            @Param("total") int total, @Param("success") int success,
                            @Param("failure") int failure, @Param("errorsJson") String errorsJson);
}
