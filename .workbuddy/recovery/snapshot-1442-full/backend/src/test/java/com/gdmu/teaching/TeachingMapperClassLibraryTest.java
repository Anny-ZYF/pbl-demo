package com.gdmu.teaching;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeachingMapperClassLibraryTest {

    private static String sqlOf(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = TeachingMapper.class.getMethod(name, parameterTypes);
        return String.join(" ", method.getAnnotation(Select.class).value());
    }

    @Test
    void classLibraryCountsOnlyActiveStudentsAndCurrentCourseEnrollment() throws Exception {
        String sql = sqlOf("importableClasses", long.class, String.class);

        // MyBatis 只在注解文本以 <script> 开头时才启用动态 SQL 解析，文本块缩进不能留前导空格
        assertThat(sql).startsWith("<script>");
        assertThat(sql).contains("FROM `class` c");
        assertThat(sql).contains("c.status='ACTIVE'");
        assertThat(sql).contains("department_id AS departmentId");
        assertThat(sql).contains("AS studentCount");
        assertThat(sql).contains("AS enrolledCount");
        assertThat(sql).contains("sc.status='ACTIVE'");
        assertThat(sql).contains("s.status=1");
        assertThat(sql).contains("keyword");
    }

    @Test
    void rosterAndClassExpansionOnlyReadActiveAccounts() throws Exception {
        String roster = sqlOf("classRoster", long.class, long.class);
        assertThat(roster).contains("AS enrolled");
        assertThat(roster).contains("sc.course_id=#{courseId}");
        assertThat(roster).contains("s.class_id=#{classId}");
        assertThat(roster).contains("s.status=1");

        String members = sqlOf("activeStudentIdsByClass", long.class);
        assertThat(members).contains("s.class_id=#{classId}");
        assertThat(members).contains("s.status=1");
    }

    @Test
    void activeClassOnlyAcceptsEnabledClasses() throws Exception {
        assertThat(sqlOf("activeClass", long.class)).contains("status='ACTIVE'");
    }

    @Test
    void classLibraryDynamicSqlActuallyParsesAndAppliesKeywordFilter() throws Exception {
        Method method = TeachingMapper.class.getMethod("importableClasses", long.class, String.class);
        String script = method.getAnnotation(Select.class).value()[0];
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);

        Map<String, Object> withKeyword = new HashMap<>();
        withKeyword.put("courseId", 301L);
        withKeyword.put("keyword", "临床");
        BoundSql filtered = source.getBoundSql(withKeyword);
        assertThat(filtered.getSql()).doesNotContain("<script>").doesNotContain("<if");
        assertThat(filtered.getSql()).contains("c.name LIKE CONCAT('%',?,'%')");

        Map<String, Object> noKeyword = new HashMap<>();
        noKeyword.put("courseId", 301L);
        noKeyword.put("keyword", "");
        assertThat(source.getBoundSql(noKeyword).getSql()).doesNotContain("c.name LIKE");
    }
}
