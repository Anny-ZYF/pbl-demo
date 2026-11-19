package com.gdmu.storage;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StorageAccessMapperTest {

    @Test
    void downloadNameUsesTheNewestAttachmentRecord() throws Exception {
        Method method = StorageAccessMapper.class.getMethod("originalNameByKey", String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        // 同一 storage_key 可能被多次提交引用；取最早一条会拿到历史的 UUID 名
        assertThat(sql).contains("ORDER BY id DESC LIMIT 1");
        assertThat(sql).doesNotContain("ORDER BY id LIMIT 1");
        assertThat(sql).contains("original_name");
    }

    @Test
    void teachersCannotReadDraftSubmissions() throws Exception {
        Method method = StorageAccessMapper.class.getMethod("teacherCanRead", long.class, String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        // Attachments staged before the team submits must stay inside the group.
        assertThat(sql).contains("s.status <> 'DRAFT'");
        assertThat(sql).contains("a.teacher_id=#{teacherId}");
    }

    @Test
    void studentsReadThroughActiveGroupMembership() throws Exception {
        Method method = StorageAccessMapper.class.getMethod("studentCanRead", long.class, String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("gm.group_id=s.group_id");
        assertThat(sql).contains("gm.student_id=#{studentId}");
        assertThat(sql).contains("gm.status='ACTIVE'");
        assertThat(sql).doesNotContain("submitter_id");
    }

    @Test
    void studentsCanDownloadDraftAttachmentsSharedByTheirGroup() throws Exception {
        Method method = StorageAccessMapper.class.getMethod("studentCanRead", long.class, String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        // Only the teacher side filters DRAFT; every ACTIVE group member may download
        // an attachment as soon as it is staged, and after the team submits as well.
        assertThat(sql).doesNotContain("s.status <> 'DRAFT'");
        assertThat(sql).contains("gm.group_id=s.group_id");
        assertThat(sql).contains("gm.student_id=#{studentId}");
        assertThat(sql).contains("gm.status='ACTIVE'");
    }
}
