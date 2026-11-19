package com.gdmu.teaching;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TeachingMapperAssignmentStatsTest {

    @Test
    void assignmentListExposesCurrentSubmissionStats() throws Exception {
        Method method = TeachingMapper.class.getMethod("teacherAssignments", long.class, long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        // 进度与待批改只认当前有效提交；按团队去重，避免同一小组历次重交被重复计入
        assertThat(sql).contains("AS submittedGroupCount");
        assertThat(sql).contains("AS pendingReviewCount");
        assertThat(sql).contains("COUNT(DISTINCT s.group_id)");
        assertThat(sql).contains("s.is_latest=1");
        assertThat(sql).contains("s.teacher_score IS NULL");
        assertThat(sql).contains("a.status<>'ARCHIVED'");
    }
}
