package com.gdmu.teaching;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** Teachers must not see DRAFT submissions or their staged attachments. */
class TeachingMapperAttachmentVisibilityTest {

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = TeachingMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", method.getAnnotation(Select.class).value());
    }

    @Test
    void teacherSubmissionListHidesDrafts() throws Exception {
        String sql = selectSql("submissions", long.class, long.class);
        assertThat(sql).contains("s.status <> 'DRAFT'");
    }

    @Test
    void teacherAttachmentQueryHidesDrafts() throws Exception {
        String sql = selectSql("teacherSubmissionAttachments", long.class, long.class);
        assertThat(sql).contains("s.status <> 'DRAFT'");
    }

    @Test
    void studentAttachmentListStaysGroupScoped() throws Exception {
        String sql = selectSql("studentSubmissionAttachments", long.class, long.class);
        // Attachments follow the group resolved in the detail view, not a possibly stale
        // or duplicated group_member row, and DRAFT attachments stay visible to the team.
        assertThat(sql).contains("s.group_id=#{groupId}");
        assertThat(sql).contains("s.is_latest=1");
        assertThat(sql).doesNotContain("#{studentId}");
        assertThat(sql).doesNotContain("status <> 'DRAFT'");
    }

    @Test
    void draftSubmissionInsertNeverPromotesAnExistingSubmission() throws Exception {
        Method method = TeachingMapper.class.getMethod("ensureDraftSubmission",
                long.class, long.class, long.class);
        String sql = String.join(" ", method.getAnnotation(Insert.class).value());

        assertThat(sql).contains("'DRAFT'");
        assertThat(sql).contains("ON DUPLICATE KEY UPDATE");
        assertThat(sql).doesNotContain("status=VALUES(status)");
        assertThat(sql).doesNotContain("status='SUBMITTED'");
    }
}
