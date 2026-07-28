-- Demo dataset for local/test environments only.
-- Existing production-like rows are not deleted or updated.
-- Teacher login: teacher01 / Teacher2026New!
-- Demo student passwords: Student2026New!

USE groupwork;

SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS seed_groupwork_demo $$
CREATE PROCEDURE seed_groupwork_demo()
BEGIN
    DECLARE v_teacher BIGINT;
    DECLARE v_course BIGINT;
    DECLARE v_assignment BIGINT;
    DECLARE v_part1 BIGINT;
    DECLARE v_part2 BIGINT;
    DECLARE v_group BIGINT;
    DECLARE v_set BIGINT;
    DECLARE v_student BIGINT;
    DECLARE v_i INT DEFAULT 1;
    DECLARE v_course_no INT DEFAULT 1;
    DECLARE v_assignment_no INT DEFAULT 1;
    DECLARE v_group_no INT DEFAULT 1;
    DECLARE v_member_no INT DEFAULT 1;
    DECLARE v_member_count INT;
    DECLARE v_student_no INT;
    DECLARE v_score DECIMAL(6,2);
    DECLARE v_course_code VARCHAR(50);
    DECLARE v_course_name VARCHAR(100);
    DECLARE v_assignment_title VARCHAR(200);

    SELECT id INTO v_teacher FROM teacher_users WHERE teacher_user_id = 'teacher01' LIMIT 1;
    IF v_teacher IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'teacher01 must exist before loading demo data';
    END IF;

    SET v_i = 1;
    WHILE v_i <= 60 DO
        INSERT INTO student_users (student_user_id, password, name, role, status, must_change_password)
        VALUES (
            CONCAT('demo_student_', LPAD(v_i, 2, '0')),
            '$2a$12$7O9FkBBiKGvG7A5O6RyuNOqpYqrfYriTJAulXX.O6PRqPbdhq7/D.',
            CONCAT('演示学生', LPAD(v_i, 2, '0')), 'ROLE_STUDENT', 1, 0
        )
        ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1, must_change_password = 0;
        SET v_i = v_i + 1;
    END WHILE;

    SET v_course_no = 1;
    WHILE v_course_no <= 2 DO
        SET v_course_code = IF(v_course_no = 1, 'DEMO-SE-2026', 'DEMO-AI-2026');
        SET v_course_name = IF(v_course_no = 1, '软件工程', '人工智能应用');
        INSERT INTO course (code, name, teacher_id, term, grade, status, student_count)
        VALUES (v_course_code, v_course_name, v_teacher, '2026-2027-1', '2024', 'ACTIVE', 30)
        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = VALUES(name), teacher_id = v_teacher, student_count = 30;
        SET v_course = LAST_INSERT_ID();
        IF v_course = 0 THEN SELECT id INTO v_course FROM course WHERE code = v_course_code; END IF;

        SET v_i = 1;
        WHILE v_i <= 30 DO
            SELECT id INTO v_student FROM student_users WHERE student_user_id = CONCAT('demo_student_', LPAD((v_course_no - 1) * 30 + v_i, 2, '0'));
            INSERT IGNORE INTO student_course (student_id, course_id, status, join_method) VALUES (v_student, v_course, 'ACTIVE', 'MANUAL');
            SET v_i = v_i + 1;
        END WHILE;

        SET v_assignment_no = 1;
        WHILE v_assignment_no <= 3 DO
            SET v_assignment_title = CASE v_assignment_no WHEN 1 THEN '需求分析' WHEN 2 THEN '系统设计' ELSE '最终项目' END;
            INSERT INTO assignment (title, description, course_id, teacher_id, deadline, status, require_contribution, allow_score_view, total_score, enable_intelligent_scoring, standard_group_size, scoring_rule_id)
            VALUES (v_assignment_title, CONCAT('演示作业：', v_assignment_title), v_course, v_teacher, '2026-12-31 23:59:59', 'PUBLISHED', 1, 1, 100, 1, 4, 1);
            SET v_assignment = LAST_INSERT_ID();
            INSERT INTO assignment_parts (assignment_id, part_name, part_description, weight, sort_order)
            VALUES (v_assignment, '研究分析', '演示评价分支', 0.400000, 1), (v_assignment, '实现表达', '演示评价分支', 0.600000, 2);
            SELECT id INTO v_part1 FROM assignment_parts WHERE assignment_id = v_assignment AND sort_order = 1;
            SELECT id INTO v_part2 FROM assignment_parts WHERE assignment_id = v_assignment AND sort_order = 2;

            SET v_group_no = 1;
            WHILE v_group_no <= 8 DO
                SET v_member_count = IF(v_group_no <= 6, 4, 3);
                INSERT INTO group_table (name, course_id, assignment_id, teacher_id, member_count, current_members, max_members, status, approval_status, team_status, join_method, average_score)
                VALUES (CONCAT('Team', LPAD(v_group_no, 2, '0')), v_course, v_assignment, v_teacher, v_member_count, v_member_count, 4, 1, 1, 'CONFIRMED', 'TEACHER_ASSIGNED', 0);
                SET v_group = LAST_INSERT_ID();

                SET v_member_no = 1;
                WHILE v_member_no <= v_member_count DO
                    SET v_student_no = IF(v_group_no <= 6, (v_group_no - 1) * 4 + v_member_no, 24 + (v_group_no - 7) * 3 + v_member_no) + (v_course_no - 1) * 30;
                    SELECT id INTO v_student FROM student_users WHERE student_user_id = CONCAT('demo_student_', LPAD(v_student_no, 2, '0'));
                    INSERT INTO group_member (group_id, student_id, assignment_id, course_id, role, status)
                    VALUES (v_group, v_student, v_assignment, v_course, IF(v_member_no = 1, 'LEADER', 'MEMBER'), 'ACTIVE');
                    SET v_member_no = v_member_no + 1;
                END WHILE;

                INSERT INTO workload_set (group_id, assignment_id, version_no, status, created_by_teacher_id, submitted_at, locked_at)
                VALUES (v_group, v_assignment, 1, 'LOCKED', v_teacher, NOW(3), NOW(3));
                SET v_set = LAST_INSERT_ID();

                SET v_member_no = 1;
                WHILE v_member_no <= v_member_count DO
                    SET v_student_no = IF(v_group_no <= 6, (v_group_no - 1) * 4 + v_member_no, 24 + (v_group_no - 7) * 3 + v_member_no) + (v_course_no - 1) * 30;
                    SELECT id INTO v_student FROM student_users WHERE student_user_id = CONCAT('demo_student_', LPAD(v_student_no, 2, '0'));
                    INSERT INTO workload_item (workload_set_id, assignment_part_id, student_id, share, status)
                    VALUES (v_set, v_part1, v_student, 1.0 / v_member_count, 'VERIFIED'),
                           (v_set, v_part2, v_student, 1.0 / v_member_count, 'VERIFIED');
                    INSERT INTO workload_confirmation (workload_set_id, student_id, status, confirmed_at)
                    VALUES (v_set, v_student, 'CONFIRMED', NOW(3));
                    SET v_member_no = v_member_no + 1;
                END WHILE;

                SET v_score = CASE v_assignment_no WHEN 1 THEN 86 WHEN 2 THEN 89 ELSE 92 END;
                INSERT INTO submission (assignment_id, group_id, attempt_no, is_latest, submitter_id, content, artifact_json, submit_time, status, teacher_score, teacher_comment, graded_by, graded_time, has_contribution, group_member_count)
                VALUES (v_assignment, v_group, 1, 1, v_student, CONCAT('Synthetic demo submission for ', v_assignment_title), JSON_OBJECT('kind', 'demo-dataset'), NOW(3), 'GRADED', v_score, '演示教师评分', v_teacher, NOW(3), 1, v_member_count);
                SET v_group_no = v_group_no + 1;
            END WHILE;
            SET v_assignment_no = v_assignment_no + 1;
        END WHILE;
        SET v_course_no = v_course_no + 1;
    END WHILE;
END $$
DELIMITER ;

CALL seed_groupwork_demo();
DROP PROCEDURE IF EXISTS seed_groupwork_demo;

SELECT 'demo dataset seeded' AS result;
SELECT c.code, c.name, COUNT(DISTINCT sc.student_id) AS students, COUNT(DISTINCT a.id) AS assignments
FROM course c
LEFT JOIN student_course sc ON sc.course_id = c.id AND sc.status = 'ACTIVE'
LEFT JOIN assignment a ON a.course_id = c.id AND a.title IN ('需求分析', '系统设计', '最终项目')
WHERE c.code IN ('DEMO-SE-2026', 'DEMO-AI-2026')
GROUP BY c.id, c.code, c.name;
