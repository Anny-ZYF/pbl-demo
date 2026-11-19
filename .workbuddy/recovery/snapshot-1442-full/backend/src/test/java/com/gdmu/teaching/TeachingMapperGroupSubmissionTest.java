package com.gdmu.teaching;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One submission belongs to the whole group. Every ACTIVE member must see the same
 * submitted flag, content and attachments, regardless of who clicked submit.
 */
class TeachingMapperGroupSubmissionTest {

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = TeachingMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", method.getAnnotation(Select.class).value());
    }

    @Test
    void assignmentListSubmittedFlagIsGroupScopedAndIgnoresDrafts() throws Exception {
        String sql = selectSql("studentAssignments", long.class, long.class);

        assertThat(sql).contains("gm.group_id=s.group_id");
        assertThat(sql).contains("gm.student_id=#{studentId}");
        assertThat(sql).contains("gm.status='ACTIVE'");
        assertThat(sql).contains("s.is_latest=1");
        assertThat(sql).contains("s.status <> 'DRAFT'");
        assertThat(sql).doesNotContain("submitter_id");
    }

    @Test
    void assignmentListScorePublishedIsScopedToTheStudentsOwnGroup() throws Exception {
        String sql = selectSql("studentAssignments", long.class, long.class);

        assertThat(sql).contains("gm.group_id=sr.group_id");
        assertThat(sql).doesNotContain("WHERE sr.assignment_id=a.id AND sr.status='PUBLISHED') AS scorePublished");
    }

    @Test
    void detailExposesOneGroupLevelSubmissionState() throws Exception {
        String sql = selectSql("studentAssignmentDetail", long.class, long.class);

        assertThat(sql).contains("sub.group_id=g.id AND sub.is_latest=1");
        assertThat(sql).contains("(sub.status IS NOT NULL AND sub.status <> 'DRAFT') AS submitted");
        assertThat(sql).contains("sub.submitter_id AS submitterId");
        assertThat(sql).contains("sub.status AS submissionStatus");
    }

    @Test
    void detailScorePublishedIsScopedToTheGroup() throws Exception {
        String sql = selectSql("studentAssignmentDetail", long.class, long.class);

        assertThat(sql).contains("sr.group_id=g.id");
    }

    @Test
    void detailResolvesGroupThroughGroupTableInsteadOfDenormalizedAssignmentId() throws Exception {
        String sql = selectSql("studentAssignmentDetail", long.class, long.class);

        // Legacy rows can carry a stale group_member.assignment_id; resolving the group
        // through group_table keeps all members of a team on the same submission.
        assertThat(sql).doesNotContain("gm.assignment_id=a.id");
        assertThat(sql).contains("gt.assignment_id=#{assignmentId}");
        assertThat(sql).contains("gm2.status='ACTIVE'");
    }

    @Test
    void assignmentListNoLongerHasATeamFormationWindow() throws Exception {
        String sql = selectSql("studentAssignments", long.class, long.class);

        assertThat(sql).contains("FALSE AS teamFormation");
        assertThat(sql).doesNotContain("INTERVAL 2 DAY");
        assertThat(sql).doesNotContain("published_at, INTERVAL 2 DAY");
    }

    @Test
    void dissolvedGroupsWithSubmissionHistoryAreNeverRecycledForANewTeam() throws Exception {
        // Reusing an old group id would attach another team's submissions/workloads to a new
        // group, which is exactly the "组 X 显示组 Y 提交内容" symptom.
        Method method = TeachingMapper.class.getMethod("reusableStudentGroup", long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("NOT EXISTS (SELECT 1 FROM submission s WHERE s.group_id=g.id)");
        assertThat(sql).contains("NOT EXISTS (SELECT 1 FROM workload_set ws WHERE ws.group_id=g.id)");
        assertThat(sql).contains("NOT EXISTS (SELECT 1 FROM scoring_run sr WHERE sr.group_id=g.id)");
        assertThat(sql).contains("gm.status='ACTIVE'");
    }

    @Test
    void attachmentsStayRemovableBeforeGradingEvenAfterTheTeamSubmits() throws Exception {
        Method method = TeachingMapper.class.getMethod("deleteStudentAttachment",
                long.class, long.class, long.class);
        String sql = String.join(" ", method.getAnnotation(Delete.class).value());

        // The group is resolved once by the service, so another team's attachment is untouchable.
        assertThat(sql).contains("sa.id=#{attachmentId}");
        assertThat(sql).contains("s.group_id=#{groupId}");
        assertThat(sql).contains("s.assignment_id=#{assignmentId}");
        assertThat(sql).contains("s.is_latest=1");
        assertThat(sql).contains("s.status IN ('DRAFT','SUBMITTED','RETURNED')");
        assertThat(sql).doesNotContain("s.status='DRAFT'");
        // Graded or published submissions are frozen.
        assertThat(sql).contains("COALESCE(s.is_score_published,0)=0");
        assertThat(sql).contains("s.teacher_score IS NULL");
    }

    @Test
    void studentAttachmentListIsScopedToTheSameResolvedGroup() throws Exception {
        String sql = selectSql("studentSubmissionAttachments", long.class, long.class);

        assertThat(sql).contains("s.group_id=#{groupId}");
        assertThat(sql).contains("s.assignment_id=#{assignmentId}");
        assertThat(sql).contains("s.is_latest=1");
        assertThat(sql).doesNotContain("#{studentId}");
    }

}
