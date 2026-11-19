package com.gdmu.admin;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** Deleted accounts must never leak back into admin lists, login names or account maintenance. */
class AdminMapperDeletionSqlTest {

    private static String select(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", method.getAnnotation(Select.class).value());
    }

    private static String update(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", method.getAnnotation(Update.class).value());
    }

    private static String delete(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", method.getAnnotation(Delete.class).value());
    }

    @Test
    void accountListsAndCountsExcludeLogicalDeletes() throws Exception {
        assertThat(select("accounts", String.class, Integer.class, String.class, int.class, int.class))
                .contains("v.deleted_at IS NULL");
        assertThat(select("accountCount", String.class, Integer.class, String.class))
                .contains("deleted_at IS NULL");
        assertThat(select("accountUsernameCount", String.class))
                .contains("deleted_at IS NULL");
        assertThat(select("dashboard")).contains("student_users WHERE deleted_at IS NULL");
        assertThat(select("organizationOverview")).contains("status = 1 AND deleted_at IS NULL");
    }

    @Test
    void accountMaintenanceNeverTouchesDeletedRows() throws Exception {
        assertThat(update("updateTeacher", long.class, String.class, Long.class))
                .contains("deleted_at IS NULL");
        assertThat(update("updateStudent", long.class, String.class, Long.class, Long.class, Integer.class))
                .contains("deleted_at IS NULL");
        assertThat(update("setTeacherStatus", long.class, int.class)).contains("deleted_at IS NULL");
        assertThat(update("setStudentStatus", long.class, int.class)).contains("deleted_at IS NULL");
        assertThat(update("resetTeacherPassword", long.class, String.class)).contains("deleted_at IS NULL");
        assertThat(update("resetStudentPassword", long.class, String.class)).contains("deleted_at IS NULL");
    }

    @Test
    void softDeleteDisablesTheAccountAndFreesTheLoginName() throws Exception {
        String teacher = update("softDeleteTeacher", long.class, String.class, long.class);
        String student = update("softDeleteStudent", long.class, String.class, long.class);
        assertThat(teacher).contains("teacher_user_id=#{tombstone}", "status=0", "deleted_at=CURRENT_TIMESTAMP(3)",
                "token_version=token_version+1", "WHERE id=#{id} AND deleted_at IS NULL");
        assertThat(student).contains("student_user_id=#{tombstone}", "status=0", "deleted_at=CURRENT_TIMESTAMP(3)",
                "token_version=token_version+1", "WHERE id=#{id} AND deleted_at IS NULL");
        assertThat(select("accountForDeletion", long.class, String.class))
                .contains("deleted_at AS deletedAt", "role=#{role}");
    }

    @Test
    void organizationDeletionChecksEveryReferenceBeforeDeleting() throws Exception {
        assertThat(select("departmentUsage", long.class))
                .contains("teacher_users", "major", "`class`", "deleted_at IS NULL");
        assertThat(select("majorUsage", long.class))
                .contains("student_users", "course", "course_major", "group_table", "deleted_at IS NULL");
        assertThat(select("classUsage", long.class))
                .contains("student_users", "group_table", "deleted_at IS NULL");
        assertThat(delete("deleteClass", long.class)).isEqualTo("DELETE FROM `class` WHERE id=#{id}");
        assertThat(delete("deleteMajor", long.class)).isEqualTo("DELETE FROM major WHERE id=#{id}");
        assertThat(delete("deleteDepartment", long.class)).isEqualTo("DELETE FROM department WHERE id=#{id}");
    }
}
