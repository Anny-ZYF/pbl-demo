package com.gdmu.teaching;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface TeachingMapper {
    @Select("""
            SELECT c.id,c.code,c.name,c.term,c.grade,c.status,c.begin_date AS beginDate,c.end_date AS endDate,
                   (SELECT COUNT(*) FROM student_course sc WHERE sc.course_id=c.id AND sc.status='ACTIVE') AS studentCount
            FROM course c WHERE c.teacher_id=#{teacherId} ORDER BY c.id DESC
            """)
    List<Map<String, Object>> teacherCourses(long teacherId);

    @Select("SELECT COUNT(*) FROM course WHERE id=#{courseId} AND teacher_id=#{teacherId}")
    int ownsCourse(@Param("courseId") long courseId, @Param("teacherId") long teacherId);

    @Select("""
            SELECT a.id,a.title,a.description,a.deadline,a.status,a.published_at AS publishedAt,a.total_score AS totalScore,
                   a.standard_group_size AS standardGroupSize,a.created_at AS createdAt,
                   (SELECT COUNT(*) FROM group_table g WHERE g.assignment_id=a.id) AS groupCount,
                   (SELECT COUNT(*) FROM submission s WHERE s.assignment_id=a.id) AS submissionCount
            FROM assignment a WHERE a.course_id=#{courseId} AND a.teacher_id=#{teacherId}
            ORDER BY a.created_at DESC
            """)
    List<Map<String, Object>> teacherAssignments(@Param("courseId") long courseId, @Param("teacherId") long teacherId);

    @Select("""
            <script>
            SELECT s.id AS studentId,s.student_user_id AS studentNo,s.name,
                   c.name AS className,m.name AS majorName,
                   <choose>
                     <when test='assignmentId != null'>
                       EXISTS(SELECT 1 FROM group_member gm JOIN group_table g ON g.id=gm.group_id
                         WHERE gm.student_id=s.id AND gm.status='ACTIVE'
                           AND g.assignment_id=#{assignmentId} AND g.team_status NOT IN ('DISSOLVED','ARCHIVED'))
                     </when>
                     <otherwise>FALSE</otherwise>
                   </choose> AS assignedToGroup
            FROM student_course sc
            JOIN student_users s ON s.id=sc.student_id
            LEFT JOIN `class` c ON c.id=s.class_id
            LEFT JOIN major m ON m.id=s.major_id
            WHERE sc.course_id=#{courseId} AND sc.status='ACTIVE' AND s.status=1
            ORDER BY s.student_user_id
            </script>
            """)
    List<Map<String, Object>> courseStudents(@Param("courseId") long courseId,
                                             @Param("assignmentId") Long assignmentId);

    @Select("""
            SELECT s.id AS studentId,s.student_user_id AS studentNo,s.name,
                   CASE WHEN g.id IS NULL THEN NULL ELSE CONCAT('Team', LPAD(COALESCE(CAST(REGEXP_SUBSTR(g.name, '[0-9]+') AS UNSIGNED), g.id), 2, '0')) END AS groupName,
                   sr.id AS scoringRunId,COALESCE(r.final_score, fs.score) AS finalScore,
                   r.rule_score AS ruleScore,r.model_score AS modelScore,r.final_score AS suggestedScore,
                   r.status AS scoringStatus,
                   CASE WHEN sr.status='PUBLISHED' THEN 1 ELSE COALESCE(i.is_published, 0) END AS scorePublished,
                   COALESCE(r.contribution, i.contribution_ratio / 100,
                     (SELECT SUM(wi.share * ap.weight) FROM workload_item wi
                      JOIN workload_set ws ON ws.id=wi.workload_set_id
                      JOIN assignment_parts ap ON ap.id=wi.assignment_part_id
                      WHERE ws.assignment_id=#{assignmentId} AND ws.group_id=g.id
                        AND ws.status='LOCKED' AND wi.student_id=s.id),
                     (SELECT 1.0 / COUNT(*) FROM group_member gm2
                      WHERE gm2.group_id=g.id AND gm2.status='ACTIVE')) AS contributionRatio,
                   COALESCE(r.group_score, i.teacher_score,
                     (SELECT sub.teacher_score FROM submission sub
                      WHERE sub.assignment_id=#{assignmentId} AND sub.group_id=g.id
                        AND sub.is_latest=1 ORDER BY sub.id DESC LIMIT 1)) AS groupScore,
                   COALESCE(r.final_score, i.individual_final_score) AS individualFinalScore
            FROM student_course sc JOIN student_users s ON s.id=sc.student_id
            LEFT JOIN group_member gm ON gm.student_id=s.id AND gm.status='ACTIVE'
              AND gm.group_id IN (SELECT id FROM group_table WHERE assignment_id=#{assignmentId})
            LEFT JOIN group_table g ON g.id=gm.group_id AND g.assignment_id=#{assignmentId}
            LEFT JOIN final_score fs ON fs.student_id=s.id AND fs.assignment_id=#{assignmentId}
            LEFT JOIN scoring_run sr ON sr.id=(
              SELECT sr2.id FROM scoring_run sr2
              JOIN score_result r2 ON r2.scoring_run_id=sr2.id AND r2.student_id=s.id
              WHERE sr2.assignment_id=#{assignmentId}
              ORDER BY sr2.id DESC LIMIT 1)
            LEFT JOIN score_result r ON r.student_id=s.id AND r.scoring_run_id=sr.id
            LEFT JOIN individual_score i ON i.student_id=s.id AND i.submission_id=(
              SELECT sub.id FROM submission sub WHERE sub.assignment_id=#{assignmentId}
                AND sub.group_id=g.id AND sub.is_latest=1 ORDER BY sub.id DESC LIMIT 1)
            WHERE sc.course_id=#{courseId} AND sc.status='ACTIVE' AND s.status=1
            ORDER BY s.student_user_id
            """)
    List<Map<String, Object>> teacherScores(@Param("courseId") long courseId,
                                            @Param("assignmentId") long assignmentId);

    @Select("SELECT id FROM student_users WHERE student_user_id=#{studentNo} AND status=1")
    Long studentIdByNo(String studentNo);

    @Insert("""
            INSERT INTO student_course(student_id,course_id,status,join_method,join_time,create_time,update_time)
            VALUES(#{studentId},#{courseId},'ACTIVE','IMPORT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE status='ACTIVE',join_method='IMPORT',join_time=CURRENT_TIMESTAMP,update_time=CURRENT_TIMESTAMP
            """)
    int importStudent(@Param("studentId") long studentId, @Param("courseId") long courseId);

    @Select("SELECT status FROM student_course WHERE student_id=#{studentId} AND course_id=#{courseId}")
    String studentCourseStatus(@Param("studentId") long studentId, @Param("courseId") long courseId);

    @Update("UPDATE student_course SET status='DROPPED',update_time=CURRENT_TIMESTAMP WHERE student_id=#{studentId} AND course_id=#{courseId} AND status='ACTIVE'")
    int dropStudent(@Param("studentId") long studentId, @Param("courseId") long courseId);

    @Insert("""
            INSERT INTO assignment(title,description,course_id,teacher_id,deadline,status,published_at,total_score,
              require_contribution,enable_intelligent_scoring,standard_group_size,created_at,updated_at)
            VALUES(#{r.title},#{r.description},#{courseId},#{teacherId},#{r.deadline},'PUBLISHED',CURRENT_TIMESTAMP,#{r.totalScore},
              1,1,#{r.standardGroupSize},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertAssignment(@Param("courseId") long courseId, @Param("teacherId") long teacherId,
                         @Param("r") AssignmentCreateRequest request);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Insert("""
            INSERT INTO assignment_parts(assignment_id,part_name,part_description,weight,sort_order,create_time,update_time)
            VALUES(#{assignmentId},#{name},#{description},#{weight},#{sortOrder},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertAssignmentPart(@Param("assignmentId") long assignmentId, @Param("name") String name,
                             @Param("description") String description, @Param("weight") double weight,
                             @Param("sortOrder") int sortOrder);

    @Update("""
            UPDATE assignment SET status='PUBLISHED',updated_at=CURRENT_TIMESTAMP
            WHERE id=#{assignmentId} AND teacher_id=#{teacherId} AND status='DRAFT'
            """)
    int publishAssignment(@Param("assignmentId") long assignmentId, @Param("teacherId") long teacherId);

    @Select("""
            SELECT g.id,g.name,g.status,g.team_status AS teamStatus,g.max_members AS maxMembers,
                   s.name AS leaderName,
                   (SELECT COUNT(*) FROM group_member gm WHERE gm.group_id=g.id AND gm.status='ACTIVE') AS memberCount
            FROM group_table g LEFT JOIN student_users s ON s.id=g.leader_student_id
            JOIN assignment a ON a.id=g.assignment_id
            WHERE g.assignment_id=#{assignmentId} AND a.teacher_id=#{teacherId} ORDER BY g.id
            """)
    List<Map<String, Object>> groups(@Param("assignmentId") long assignmentId, @Param("teacherId") long teacherId);

    @Select("""
            SELECT COUNT(*) FROM group_table g JOIN assignment a ON a.id=g.assignment_id
            WHERE g.id=#{groupId} AND g.assignment_id=#{assignmentId} AND a.teacher_id=#{teacherId}
              AND g.team_status NOT IN ('DISSOLVED','ARCHIVED')
            """)
    int ownsGroup(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId,
                  @Param("teacherId") long teacherId);

    @Select("""
            SELECT s.id AS studentId,s.student_user_id AS studentNo,s.name,gm.role,gm.status,
                   c.name AS className,gm.joined_at AS joinedAt
            FROM group_member gm JOIN student_users s ON s.id=gm.student_id
            LEFT JOIN `class` c ON c.id=s.class_id
            WHERE gm.group_id=#{groupId} ORDER BY CASE gm.role WHEN 'LEADER' THEN 0 ELSE 1 END,s.student_user_id
            """)
    List<Map<String, Object>> groupMembers(long groupId);

    @Select("""
            SELECT s.id AS studentId,s.student_user_id AS studentNo,s.name,gm.role,gm.status,
                   c.name AS className,gm.joined_at AS joinedAt,
                   COALESCE((SELECT i.contribution_ratio FROM individual_score i
                             JOIN submission sub ON sub.id=i.submission_id
                             WHERE i.student_id=s.id AND sub.assignment_id=#{assignmentId}
                               AND sub.group_id=gm.group_id AND sub.is_latest=1
                             ORDER BY i.id DESC LIMIT 1),
                            (SELECT SUM(wi.share * ap.weight) * 100 FROM workload_item wi
                             JOIN workload_set ws ON ws.id=wi.workload_set_id
                             JOIN assignment_parts ap ON ap.id=wi.assignment_part_id
                             WHERE ws.assignment_id=#{assignmentId} AND ws.group_id=gm.group_id
                               AND ws.status='LOCKED' AND wi.student_id=s.id)) AS contributionRatio
            FROM group_member gm JOIN student_users s ON s.id=gm.student_id
            LEFT JOIN `class` c ON c.id=s.class_id
            WHERE gm.group_id=#{groupId} AND gm.status='ACTIVE'
            ORDER BY CASE gm.role WHEN 'LEADER' THEN 0 ELSE 1 END,s.student_user_id
            """)
    List<Map<String, Object>> studentGroupMembers(@Param("groupId") long groupId,
                                                  @Param("assignmentId") long assignmentId);

    @Select("SELECT COUNT(*) FROM assignment WHERE id=#{assignmentId} AND teacher_id=#{teacherId}")
    int ownsAssignment(@Param("assignmentId") long assignmentId, @Param("teacherId") long teacherId);

    @Select("SELECT course_id FROM assignment WHERE id=#{assignmentId} AND teacher_id=#{teacherId}")
    Long assignmentCourse(@Param("assignmentId") long assignmentId, @Param("teacherId") long teacherId);

    @Select("SELECT standard_group_size FROM assignment WHERE id=#{assignmentId} AND teacher_id=#{teacherId}")
    Integer assignmentStandardGroupSize(@Param("assignmentId") long assignmentId, @Param("teacherId") long teacherId);

    @Select("""
            <script>
            SELECT COUNT(DISTINCT sc.student_id) FROM student_course sc
            WHERE sc.course_id=#{courseId} AND sc.status='ACTIVE' AND sc.student_id IN
            <foreach collection='studentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int eligibleStudentCount(@Param("courseId") long courseId, @Param("studentIds") List<Long> studentIds);

    @Select("""
            <script>
            SELECT COUNT(*) FROM group_member gm JOIN group_table g ON g.id=gm.group_id
            WHERE g.assignment_id=#{assignmentId} AND gm.status='ACTIVE' AND gm.student_id IN
            <foreach collection='studentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int existingAssignmentMemberships(@Param("assignmentId") long assignmentId,
                                      @Param("studentIds") List<Long> studentIds);

    @Insert("""
            INSERT INTO group_table(name,course_id,assignment_id,leader_student_id,status,team_status,max_members,
              description,create_time,update_time)
            VALUES(#{r.name},#{courseId},#{assignmentId},#{r.leaderStudentId},1,'CONFIRMED',#{memberCount},
              #{r.description},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertGroup(@Param("assignmentId") long assignmentId, @Param("courseId") long courseId,
                    @Param("memberCount") int memberCount, @Param("r") GroupCreateRequest request);

    @Insert("""
            INSERT INTO group_member(group_id,assignment_id,student_id,role,status,joined_at)
            VALUES(#{groupId},#{assignmentId},#{studentId},#{role},'ACTIVE',CURRENT_TIMESTAMP)
            """)
    int insertGroupMember(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId,
                          @Param("studentId") long studentId, @Param("role") String role);

    @Select("""
            SELECT s.id,s.assignment_id AS assignmentId,s.group_id AS groupId,g.name AS groupName,
                   s.attempt_no AS attemptNo,s.is_latest AS latest,
                   s.content,s.file_path AS filePath,s.submit_note AS note,
                   s.submit_time AS submitTime,s.status,s.teacher_score AS teacherScore,
                   s.teacher_comment AS teacherComment,s.is_score_published AS scorePublished,
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT('id',sa.id,'storageKey',sa.storage_key,
                           'originalName',sa.original_name,'fileSize',sa.file_size))
                    FROM submission_attachment sa WHERE sa.submission_id=s.id) AS attachments
            FROM submission s JOIN assignment a ON a.id=s.assignment_id JOIN group_table g ON g.id=s.group_id
            WHERE a.teacher_id=#{teacherId} AND a.id=#{assignmentId} AND s.is_latest=1
            ORDER BY s.submit_time DESC
            """)
    List<Map<String, Object>> submissions(@Param("assignmentId") long assignmentId, @Param("teacherId") long teacherId);

    @Select("""
            SELECT sa.id,sa.storage_key AS storageKey,sa.original_name AS originalName,sa.file_size AS fileSize
            FROM submission_attachment sa
            JOIN submission s ON s.id=sa.submission_id
            JOIN assignment a ON a.id=s.assignment_id
            WHERE sa.submission_id=#{submissionId} AND a.teacher_id=#{teacherId}
            ORDER BY sa.id
            """)
    List<Map<String, Object>> teacherSubmissionAttachments(@Param("submissionId") long submissionId,
                                                            @Param("teacherId") long teacherId);

    @Select("""
            SELECT c.id,c.code,c.name,c.term,c.grade,c.begin_date AS beginDate,c.end_date AS endDate,t.name AS teacherName
            FROM student_course sc JOIN course c ON c.id=sc.course_id JOIN teacher_users t ON t.id=c.teacher_id
            WHERE sc.student_id=#{studentId} AND sc.status='ACTIVE' AND c.status='ACTIVE' ORDER BY c.id DESC
            """)
    List<Map<String, Object>> studentCourses(long studentId);

    @Select("""
            SELECT a.id,a.title,a.description,a.deadline,a.published_at AS publishedAt,a.status,a.total_score AS totalScore,
                   a.standard_group_size AS standardGroupSize,
                   (a.published_at IS NOT NULL AND CURRENT_TIMESTAMP < DATE_ADD(a.published_at, INTERVAL 2 DAY)) AS teamFormation,
                   EXISTS(SELECT 1 FROM scoring_run sr WHERE sr.assignment_id=a.id AND sr.status='PUBLISHED') AS scorePublished,
                   EXISTS(SELECT 1 FROM submission s JOIN group_member gm ON gm.group_id=s.group_id
                          WHERE s.assignment_id=a.id AND s.is_latest=1
                            AND gm.student_id=#{studentId} AND gm.status='ACTIVE') AS submitted
            FROM assignment a JOIN student_course sc ON sc.course_id=a.course_id
            WHERE sc.student_id=#{studentId} AND sc.status='ACTIVE' AND a.course_id=#{courseId}
              AND a.status IN ('PUBLISHED','CLOSED') ORDER BY a.deadline
            """)
    List<Map<String, Object>> studentAssignments(@Param("courseId") long courseId, @Param("studentId") long studentId);

    @Select("""
            SELECT a.id,a.title,a.description,a.deadline,a.published_at AS publishedAt,a.total_score AS totalScore,
                   a.standard_group_size AS standardGroupSize,
                   g.id AS groupId,g.name AS groupName,
                   sub.content,sub.file_path AS filePath,sub.submit_note AS submitNote,
                   sub.status AS submissionStatus,sub.teacher_score AS teacherScore,
                   sub.teacher_comment AS teacherComment,
                   (sub.is_score_published=1 OR EXISTS(SELECT 1 FROM scoring_run sr WHERE sr.assignment_id=a.id AND sr.status='PUBLISHED')) AS scorePublished
            FROM assignment a JOIN student_course sc ON sc.course_id=a.course_id
            LEFT JOIN group_member gm ON gm.student_id=sc.student_id AND gm.assignment_id=a.id AND gm.status='ACTIVE'
            LEFT JOIN group_table g ON g.id=gm.group_id AND g.assignment_id=a.id
            LEFT JOIN submission sub ON sub.assignment_id=a.id AND sub.group_id=g.id AND sub.is_latest=1
            WHERE a.id=#{assignmentId} AND sc.student_id=#{studentId} AND sc.status='ACTIVE'
              AND a.status IN ('PUBLISHED','CLOSED')
            LIMIT 1
            """)
    Map<String, Object> studentAssignmentDetail(@Param("assignmentId") long assignmentId,
                                                @Param("studentId") long studentId);

    @Select("SELECT id,part_name AS name,part_description AS description,weight FROM assignment_parts WHERE assignment_id=#{assignmentId} ORDER BY sort_order,id")
    List<Map<String,Object>> studentAssignmentParts(long assignmentId);

    @Select("SELECT sa.id,sa.storage_key AS storageKey,sa.original_name AS originalName,sa.file_size AS fileSize,sa.created_at AS createdAt FROM submission_attachment sa JOIN submission s ON s.id=sa.submission_id JOIN group_member gm ON gm.group_id=s.group_id AND gm.student_id=#{studentId} AND gm.status='ACTIVE' WHERE s.assignment_id=#{assignmentId} AND s.is_latest=1 ORDER BY sa.id")
    List<Map<String,Object>> studentSubmissionAttachments(@Param("assignmentId") long assignmentId,@Param("studentId") long studentId);

    @Delete("DELETE sa FROM submission_attachment sa JOIN submission s ON s.id=sa.submission_id JOIN group_member gm ON gm.group_id=s.group_id AND gm.student_id=#{studentId} AND gm.status='ACTIVE' WHERE sa.id=#{attachmentId} AND s.assignment_id=#{assignmentId} AND s.is_latest=1")
    int deleteStudentAttachment(@Param("attachmentId") long attachmentId,@Param("assignmentId") long assignmentId,@Param("studentId") long studentId);

    @Select("SELECT id FROM submission WHERE assignment_id=#{assignmentId} AND group_id=#{groupId} AND is_latest=1")
    Long latestSubmissionId(@Param("assignmentId") long assignmentId,@Param("groupId") long groupId);

    @Insert("INSERT IGNORE INTO submission_attachment(submission_id,storage_key,original_name,file_size) VALUES(#{submissionId},#{storageKey},#{originalName},#{fileSize})")
    int insertAttachment(@Param("submissionId") long submissionId,@Param("storageKey") String storageKey,@Param("originalName") String originalName,@Param("fileSize") long fileSize);

    @Select("""
            SELECT g.id,g.name,g.assignment_id AS assignmentId,g.team_status AS teamStatus,
                   g.leader_student_id AS leaderStudentId
            FROM group_member gm JOIN group_table g ON g.id=gm.group_id
            WHERE gm.student_id=#{studentId} AND gm.status='ACTIVE' ORDER BY g.id DESC
            """)
    List<Map<String, Object>> studentGroups(long studentId);

    @Select("SELECT g.id,g.name,CAST(SUBSTRING(g.name,2,LENGTH(g.name)-3) AS UNSIGNED) AS groupNo,g.max_members AS maxMembers,COUNT(gm.id) AS memberCount FROM group_table g LEFT JOIN group_member gm ON gm.group_id=g.id AND gm.status='ACTIVE' WHERE g.assignment_id=#{assignmentId} AND g.team_status NOT IN ('DISSOLVED','ARCHIVED') GROUP BY g.id ORDER BY g.id")
    List<Map<String,Object>> studentAssignmentGroups(long assignmentId);

    @Delete("DELETE FROM group_member WHERE group_id=#{groupId} AND student_id=#{studentId} AND status='ACTIVE'")
    int leaveStudentGroup(@Param("groupId") long groupId,@Param("studentId") long studentId);

    @Update("UPDATE group_table SET team_status='DISSOLVED',update_time=CURRENT_TIMESTAMP WHERE id=#{groupId} AND NOT EXISTS (SELECT 1 FROM group_member WHERE group_id=#{groupId} AND status='ACTIVE')")
    int dissolveEmptyGroup(long groupId);

    @Select("SELECT standard_group_size FROM assignment WHERE id=#{assignmentId} AND status IN ('PUBLISHED','CLOSED')")
    Integer studentAssignmentGroupSize(long assignmentId);

    @Select("SELECT COUNT(*) + 1 FROM group_table WHERE assignment_id=#{assignmentId} AND team_status NOT IN ('DISSOLVED','ARCHIVED')")
    int nextGroupNo(long assignmentId);

    @Insert("INSERT INTO group_table(name,course_id,assignment_id,leader_student_id,status,team_status,max_members,join_method,create_time,update_time) VALUES(#{name},#{courseId},#{assignmentId},#{studentId},1,'FORMING',#{maxMembers},'FREE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
    int insertStudentGroup(@Param("name") String name,@Param("courseId") long courseId,@Param("assignmentId") long assignmentId,@Param("studentId") long studentId,@Param("maxMembers") int maxMembers);

    @Select("SELECT LAST_INSERT_ID()") long lastGroupId();

    @Insert("INSERT INTO group_member(group_id,assignment_id,student_id,role,status,joined_at) VALUES(#{groupId},#{assignmentId},#{studentId},'LEADER','ACTIVE',CURRENT_TIMESTAMP)")
    int insertStudentLeader(@Param("groupId") long groupId,@Param("assignmentId") long assignmentId,@Param("studentId") long studentId);

    @Select("SELECT g.id,g.course_id AS courseId,g.max_members AS maxMembers FROM group_table g WHERE g.assignment_id=#{assignmentId} AND g.name=#{name} AND g.team_status NOT IN ('DISSOLVED','ARCHIVED')")
    Map<String,Object> findStudentGroup(@Param("assignmentId") long assignmentId,@Param("name") String name);

    @Select("SELECT COUNT(*) FROM group_member WHERE group_id=#{groupId} AND status='ACTIVE'") int activeGroupMemberCount(long groupId);

    @Insert("INSERT INTO group_member(group_id,assignment_id,student_id,role,status,joined_at) VALUES(#{groupId},#{assignmentId},#{studentId},'MEMBER','ACTIVE',CURRENT_TIMESTAMP)")
    int joinStudentGroup(@Param("groupId") long groupId,@Param("assignmentId") long assignmentId,@Param("studentId") long studentId);

    @Select("""
            SELECT COUNT(*) FROM group_member gm JOIN group_table g ON g.id=gm.group_id
            WHERE gm.group_id=#{groupId} AND gm.student_id=#{studentId} AND gm.status='ACTIVE'
              AND g.team_status NOT IN ('DISSOLVED','ARCHIVED')
            """)
    int studentOwnsGroup(@Param("groupId") long groupId, @Param("studentId") long studentId);

    @Select("""
            SELECT g.id FROM group_table g JOIN group_member gm ON gm.group_id=g.id
            WHERE g.assignment_id=#{assignmentId} AND gm.student_id=#{studentId} AND gm.status='ACTIVE'
            LIMIT 1
            """)
    Long studentGroup(@Param("assignmentId") long assignmentId, @Param("studentId") long studentId);

    @Select("SELECT id FROM assignment_parts WHERE assignment_id=#{assignmentId} ORDER BY sort_order,id")
    List<Long> assignmentPartIds(long assignmentId);
    @Select("SELECT student_id FROM group_member WHERE group_id=#{groupId} AND assignment_id=#{assignmentId} AND status='ACTIVE' ORDER BY student_id")
    List<Long> activeGroupStudentIds(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId);
    @Select("SELECT COALESCE(MAX(version_no),0)+1 FROM workload_set WHERE group_id=#{groupId} AND assignment_id=#{assignmentId}")
    int nextSubmissionWorkloadVersion(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId);
    @Update("UPDATE workload_set SET status='SUPERSEDED',updated_at=CURRENT_TIMESTAMP WHERE group_id=#{groupId} AND assignment_id=#{assignmentId} AND status IN ('DRAFT','PENDING_CONFIRMATION')")
    int supersedeSubmissionWorkloads(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId);
    @Insert("INSERT INTO workload_set(group_id,assignment_id,version_no,status,created_by_student_id,submitted_at,locked_at,created_at,updated_at) VALUES(#{groupId},#{assignmentId},#{version},'LOCKED',#{studentId},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
    int insertSubmissionWorkloadSet(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId, @Param("version") int version, @Param("studentId") long studentId);
    @Select("SELECT id FROM workload_set WHERE group_id=#{groupId} AND assignment_id=#{assignmentId} AND version_no=#{version}")
    long submissionWorkloadSetId(@Param("groupId") long groupId, @Param("assignmentId") long assignmentId, @Param("version") int version);
    @Insert("INSERT INTO workload_item(workload_set_id,assignment_part_id,student_id,share,status,created_at,updated_at) VALUES(#{setId},#{partId},#{studentId},#{share},'VERIFIED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
    int insertSubmissionWorkloadItem(@Param("setId") long setId, @Param("partId") long partId, @Param("studentId") long studentId, @Param("share") java.math.BigDecimal share);

    @Select("SELECT a.course_id FROM assignment a JOIN student_course sc ON sc.course_id=a.course_id AND sc.student_id=#{studentId} AND sc.status='ACTIVE' WHERE a.id=#{assignmentId} AND a.status IN ('PUBLISHED','CLOSED')")
    Long assignmentCourseForStudent(@Param("assignmentId") long assignmentId,@Param("studentId") long studentId);

    @Select("""
            SELECT status,is_score_published AS isScorePublished
            FROM submission WHERE assignment_id=#{assignmentId} AND group_id=#{groupId} AND is_latest=1
            FOR UPDATE
            """)
    Map<String, Object> lockSubmission(@Param("assignmentId") long assignmentId,
                                       @Param("groupId") long groupId);

    @Insert("""
            INSERT INTO submission(assignment_id,group_id,submitter_id,content,file_path,submit_note,submit_time,status,
              created_at,updated_at)
            VALUES(#{assignmentId},#{groupId},#{studentId},#{r.content},#{r.filePath},#{r.note},CURRENT_TIMESTAMP,
              'SUBMITTED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE submitter_id=VALUES(submitter_id),content=VALUES(content),file_path=VALUES(file_path),
              submit_note=VALUES(submit_note),submit_time=CURRENT_TIMESTAMP,status='SUBMITTED',
              teacher_score=NULL,teacher_comment=NULL,graded_by=NULL,graded_time=NULL,is_score_published=0,
              updated_at=CURRENT_TIMESTAMP
            """)
    int upsertSubmission(@Param("assignmentId") long assignmentId, @Param("groupId") long groupId,
                         @Param("studentId") long studentId, @Param("r") StudentSubmissionRequest request);

    @Select("SELECT deadline FROM assignment WHERE id=#{assignmentId} AND status='PUBLISHED'")
    LocalDateTime assignmentDeadline(long assignmentId);

    @Select("SELECT published_at FROM assignment WHERE id=#{assignmentId} AND status IN ('PUBLISHED','CLOSED')")
    LocalDateTime assignmentPublishedAt(long assignmentId);
}
