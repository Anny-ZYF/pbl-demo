package com.gdmu.storage;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StorageAccessMapper {
    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM submission s
              JOIN assignment a ON a.id=s.assignment_id
              WHERE (s.file_path=#{key} OR EXISTS(
                SELECT 1 FROM submission_attachment sa
                WHERE sa.submission_id=s.id AND sa.storage_key=#{key}
              )) AND a.teacher_id=#{teacherId}
            )
            """)
    boolean teacherCanRead(@Param("teacherId") long teacherId, @Param("key") String key);

    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM submission s
              JOIN group_member gm ON gm.group_id=s.group_id
              WHERE (s.file_path=#{key} OR EXISTS(
                SELECT 1 FROM submission_attachment sa
                WHERE sa.submission_id=s.id AND sa.storage_key=#{key}
              )) AND gm.student_id=#{studentId} AND gm.status='ACTIVE'
            )
            """)
    boolean studentCanRead(@Param("studentId") long studentId, @Param("key") String key);
}
