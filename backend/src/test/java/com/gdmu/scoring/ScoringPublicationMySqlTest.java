package com.gdmu.scoring;

import com.gdmu.common.BusinessException;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import com.gdmu.teaching.TeachingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Runs against an explicitly supplied disposable MySQL database; fixtures roll back after each test. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "GROUPWORK_MYSQL_TEST_URL", matches = ".+")
class ScoringPublicationMySqlTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("GROUPWORK_MYSQL_TEST_URL");
        if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/groupwork_[a-z0-9_]+_test(\\?.*)?")) {
            throw new IllegalArgumentException("Use a loopback groupwork_*_test database for MySQL regression tests");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("GROUPWORK_MYSQL_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("GROUPWORK_MYSQL_TEST_PASSWORD"));
        properties.add("app.jwt.secret-base64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        properties.add("app.bootstrap-admin.username", () -> "");
        properties.add("app.bootstrap-admin.password", () -> "");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ScoringService service;
    @Autowired TeachingMapper teaching;
    private UserPrincipal teacher;
    private long courseId;
    private long assignmentId;
    private long ruleId;
    private long[] students;
    private long groupA;
    private long groupB;
    private long submissionA;
    private long submissionB;

    @BeforeEach
    void fixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long teacherId = insert("INSERT INTO teacher_users(teacher_user_id,password,name) VALUES(?,?,'Publication test teacher')", "PT-" + suffix, "not-a-login-hash");
        teacher = new UserPrincipal(teacherId, "PT-" + suffix, "Test teacher", Role.TEACHER, "not-a-login-hash", true, false, 0);
        courseId = insert("INSERT INTO course(name,teacher_id,status) VALUES('Publication regression',?,'ACTIVE')", teacherId);
        ruleId = jdbc.queryForObject("SELECT id FROM scoring_rule WHERE status='ACTIVE' ORDER BY id LIMIT 1", Long.class);
        assignmentId = insert("INSERT INTO assignment(title,course_id,teacher_id,deadline,status,scoring_rule_id) VALUES('Publication regression',?,?,DATE_ADD(NOW(),INTERVAL 10 DAY),'PUBLISHED',?)", courseId, teacherId, ruleId);
        students = new long[3];
        for (int index = 0; index < students.length; index++) {
            students[index] = insert("INSERT INTO student_users(student_user_id,password,name) VALUES(?,?,'Publication test student')", "PS" + index + suffix, "not-a-login-hash");
            jdbc.update("INSERT INTO student_course(student_id,course_id,status) VALUES(?,?,'ACTIVE')", students[index], courseId);
        }
        groupA = group("Group A", students[0], students[1]);
        groupB = group("Group B", students[2]);
        submissionA = submission(groupA);
        submissionB = submission(groupB);
    }

    @Test
    void publishesOnlySelectedRunAndItsOwnProjections() {
        long historical = run(groupA, submissionA, "APPROVED", "REVIEWED", 71, students[0], students[1]);
        long otherGroup = run(groupB, submissionB, "APPROVED", "REVIEWED", 72, students[2]);
        long selected = run(groupA, submissionA, "APPROVED", "REVIEWED", 86, students[0], students[1]);
        var historicalBefore = results(historical);
        var otherBefore = results(otherGroup);

        service.publish(teacher, selected);

        assertThat(status(selected)).isEqualTo("PUBLISHED");
        assertThat(status(historical)).isEqualTo("APPROVED");
        assertThat(status(otherGroup)).isEqualTo("APPROVED");
        assertThat(results(historical)).isEqualTo(historicalBefore);
        assertThat(results(otherGroup)).isEqualTo(otherBefore);
        assertThat(jdbc.queryForObject("SELECT is_score_published FROM submission WHERE id=?", Integer.class, submissionB)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM final_score WHERE assignment_id=?", Integer.class, assignmentId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM individual_score WHERE submission_id=?", Integer.class, submissionB)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM final_score fs JOIN score_result r ON r.id=fs.score_result_id WHERE fs.assignment_id=? AND r.scoring_run_id=?", Integer.class, assignmentId, selected)).isEqualTo(2);
    }

    @Test
    void rejectsUnreviewedAndPartiallyReviewedRunsWithoutPublishingAnyRows() {
        long unreviewed = run(groupA, submissionA, "DEGRADED", "DRAFT", 80, students[0], students[1]);
        assertThatThrownBy(() -> service.publish(teacher, unreviewed)).isInstanceOf(BusinessException.class);
        assertThat(status(unreviewed)).isEqualTo("DEGRADED");
        long partial = run(groupA, submissionA, "APPROVED", "REVIEWED", 80, students[0], students[1]);
        jdbc.update("UPDATE score_result SET status='DRAFT' WHERE scoring_run_id=? AND student_id=?", partial, students[1]);
        assertThatThrownBy(() -> service.publish(teacher, partial)).isInstanceOf(BusinessException.class);
        assertThat(status(partial)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_result r JOIN scoring_run sr ON sr.id=r.scoring_run_id WHERE sr.assignment_id=? AND r.status='PUBLISHED'", Integer.class, assignmentId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM final_score WHERE assignment_id=?", Integer.class, assignmentId)).isZero();
    }

    @Test
    void completeReviewIsRequiredBeforePublication() {
        long selected = run(groupA, submissionA, "DEGRADED", "DRAFT", 80, students[0], students[1]);
        List<ScoreReviewRequest.Decision> decisions = jdbc.queryForList("SELECT id FROM score_result WHERE scoring_run_id=? ORDER BY id", selected).stream()
                .map(row -> new ScoreReviewRequest.Decision(((Number) row.get("id")).longValue(), 82.0, "Verified original work")).toList();
        service.reviewAndApprove(teacher, selected, new ScoreReviewRequest(decisions));
        assertThat(status(selected)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_result WHERE scoring_run_id=? AND status='REVIEWED' AND reviewed_by_teacher_id=?", Integer.class, selected, teacher.id())).isEqualTo(2);
        service.publish(teacher, selected);
        assertThat(jdbc.queryForObject("SELECT score FROM final_score WHERE student_id=? AND assignment_id=?", Double.class, students[0], assignmentId)).isEqualTo(82.0);
    }

    @Test
    void repeatedPublicationPreservesTimestampsAuditAndAdjustedProjections() {
        long selected = run(groupA, submissionA, "APPROVED", "REVIEWED", 80, students[0], students[1]);
        service.publish(teacher, selected);
        // Represents a later appeal projection; publication must not overwrite it with the original score.
        jdbc.update("UPDATE final_score SET score=87 WHERE assignment_id=? AND student_id=?", assignmentId, students[0]);
        jdbc.update("UPDATE individual_score SET individual_final_score=87 WHERE submission_id=? AND student_id=?", submissionA, students[0]);
        var before = results(selected);
        var projectionBefore = jdbc.queryForList("SELECT * FROM final_score WHERE assignment_id=? ORDER BY id", assignmentId);
        var submissionBefore = jdbc.queryForMap("SELECT * FROM submission WHERE id=?", submissionA);
        service.publish(teacher, selected);
        assertThat(results(selected)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM final_score WHERE assignment_id=? ORDER BY id", assignmentId)).isEqualTo(projectionBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM submission WHERE id=?", submissionA)).isEqualTo(submissionBefore);
        assertThat(jdbc.queryForObject("SELECT individual_final_score FROM individual_score WHERE submission_id=? AND student_id=?", Double.class, submissionA, students[0])).isEqualTo(87.0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE event_type='SCORES_PUBLISHED' AND aggregate_id=?", Integer.class, Long.toString(selected))).isEqualTo(1);
    }

    @Test
    void newUnpublishedRunDoesNotInheritThePreviousPublicationBadge() {
        long published = run(groupA, submissionA, "APPROVED", "REVIEWED", 80, students[0], students[1]);
        service.publish(teacher, published);
        long draft = run(groupA, submissionA, "DEGRADED", "DRAFT", 85, students[0], students[1]);
        Map<String, Object> row = teaching.teacherScores(courseId, assignmentId).stream()
                .filter(item -> ((Number) item.get("studentId")).longValue() == students[0]).findFirst().orElseThrow();
        assertThat(((Number) row.get("scoringRunId")).longValue()).isEqualTo(draft);
        assertThat(row.get("runStatus")).isEqualTo("DEGRADED");
        assertThat(((Number) row.get("scorePublished")).intValue()).isZero();
        assertThat(status(published)).isEqualTo("PUBLISHED");
    }

    private long group(String name, long... members) {
        long id = insert("INSERT INTO group_table(name,course_id,assignment_id,leader_student_id) VALUES(?,?,?,?)", name, courseId, assignmentId, members[0]);
        for (long member : members) jdbc.update("INSERT INTO group_member(group_id,assignment_id,student_id,status) VALUES(?,?,?,'ACTIVE')", id, assignmentId, member);
        return id;
    }

    private long submission(long group) {
        return insert("INSERT INTO submission(assignment_id,group_id,status,teacher_score) VALUES(?,?,'GRADED',80)", assignmentId, group);
    }

    private long run(long group, long submission, String status, String resultStatus, double score, long... members) {
        long id = insert("INSERT INTO scoring_run(run_no,assignment_id,group_id,submission_id,scoring_rule_id,status,standard_group_size,actual_group_size,group_score,triggered_by_teacher_id) VALUES(?,?,?,?,?,?,4,?,80,?)", UUID.randomUUID().toString(), assignmentId, group, submission, ruleId, status, members.length, teacher.id());
        for (long member : members) insert("INSERT INTO score_result(scoring_run_id,student_id,contribution,group_score,rule_score,final_score,status,explanation_json) VALUES(?,?,?,80,?,?,?,'{}')", id, member, 1.0 / members.length, score, score, resultStatus);
        return id;
    }

    private String status(long run) {
        return jdbc.queryForObject("SELECT status FROM scoring_run WHERE id=?", String.class, run);
    }

    private List<Map<String, Object>> results(long run) {
        return jdbc.queryForList("SELECT * FROM score_result WHERE scoring_run_id=? ORDER BY id", run);
    }

    private long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
}
