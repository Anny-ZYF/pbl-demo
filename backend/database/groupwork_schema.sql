-- Groupwork production core schema
-- Target: MySQL 8.0.29+
-- Scope: structure only. Import into an empty database selected by the client.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+08:00';
SET sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE TABLE IF NOT EXISTS department (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    teacher_count INT NOT NULL DEFAULT 0,
    student_count INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_name (name),
    KEY idx_department_status (status),
    CONSTRAINT chk_department_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='院系';

CREATE TABLE IF NOT EXISTS major (
    id BIGINT NOT NULL AUTO_INCREMENT,
    department_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    student_count INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_major_department_name (department_id, name),
    KEY idx_major_status (status),
    CONSTRAINT fk_major_department FOREIGN KEY (department_id) REFERENCES department (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_major_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业';

CREATE TABLE IF NOT EXISTS `class` (
    id INT NOT NULL AUTO_INCREMENT,
    major_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    grade VARCHAR(4) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    student_count INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_major_name_grade (major_id, name, grade),
    KEY idx_class_status (status),
    CONSTRAINT fk_class_major FOREIGN KEY (major_id) REFERENCES major (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_class_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班级';

CREATE TABLE IF NOT EXISTS admin_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt hash only',
    display_name VARCHAR(100) NULL,
    phone VARCHAR(20) NULL,
    admin_level INT NOT NULL DEFAULT 1,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_ADMIN',
    status TINYINT(1) NOT NULL DEFAULT 1,
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    token_version INT NOT NULL DEFAULT 0,
    last_login_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username),
    KEY idx_admin_status (status),
    CONSTRAINT chk_admin_role CHECK (role IN ('ADMIN', 'ROLE_ADMIN')),
    CONSTRAINT chk_admin_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_admin_must_change CHECK (must_change_password IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员账户';

CREATE TABLE IF NOT EXISTS teacher_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_user_id VARCHAR(20) NOT NULL,
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt hash only',
    name VARCHAR(100) NULL,
    department_id BIGINT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_TEACHER',
    status TINYINT(1) NOT NULL DEFAULT 1,
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    token_version INT NOT NULL DEFAULT 0,
    last_login_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_teacher_user_id (teacher_user_id),
    KEY idx_teacher_department (department_id),
    KEY idx_teacher_status (status),
    CONSTRAINT fk_teacher_department FOREIGN KEY (department_id) REFERENCES department (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_teacher_role CHECK (role IN ('TEACHER', 'ROLE_TEACHER')),
    CONSTRAINT chk_teacher_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_teacher_must_change CHECK (must_change_password IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师账户';

CREATE TABLE IF NOT EXISTS student_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_user_id VARCHAR(20) NOT NULL,
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt hash only',
    name VARCHAR(100) NULL,
    major_id BIGINT NULL,
    class_id INT NULL,
    grade INT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_STUDENT',
    status TINYINT(1) NOT NULL DEFAULT 1,
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    token_version INT NOT NULL DEFAULT 0,
    last_login_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_user_id (student_user_id),
    KEY idx_student_major (major_id),
    KEY idx_student_class (class_id),
    KEY idx_student_status (status),
    CONSTRAINT fk_student_major FOREIGN KEY (major_id) REFERENCES major (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_student_class FOREIGN KEY (class_id) REFERENCES `class` (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_student_role CHECK (role IN ('STUDENT', 'ROLE_STUDENT')),
    CONSTRAINT chk_student_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_student_must_change CHECK (must_change_password IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生账户';

CREATE OR REPLACE VIEW user_account_view AS
SELECT id,
       username,
       password AS password_hash,
       COALESCE(display_name, username) AS display_name,
       'ADMIN' AS role,
       status,
       must_change_password,
       token_version,
       'ADMIN' AS account_type
FROM admin_users
UNION ALL
SELECT id,
       teacher_user_id AS username,
       password AS password_hash,
       COALESCE(name, teacher_user_id) AS display_name,
       'TEACHER' AS role,
       status,
       must_change_password,
       token_version,
       'TEACHER' AS account_type
FROM teacher_users
UNION ALL
SELECT id,
       student_user_id AS username,
       password AS password_hash,
       COALESCE(name, student_user_id) AS display_name,
       'STUDENT' AS role,
       status,
       must_change_password,
       token_version,
       'STUDENT' AS account_type
FROM student_users;

CREATE TABLE IF NOT EXISTS course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NULL,
    name VARCHAR(100) NOT NULL,
    teacher_id BIGINT NULL,
    major_id BIGINT NULL COMMENT '兼容旧库；多专业关系以 course_major 为准',
    term VARCHAR(32) NULL,
    grade VARCHAR(4) NULL,
    begin_date DATETIME(3) NULL,
    end_date DATETIME(3) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    student_count INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_code (code),
    KEY idx_course_teacher (teacher_id),
    KEY idx_course_major_compat (major_id),
    KEY idx_course_status_term (status, term),
    CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_course_major_compat FOREIGN KEY (major_id) REFERENCES major (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_course_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT chk_course_dates CHECK (end_date IS NULL OR begin_date IS NULL OR end_date >= begin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程';

CREATE TABLE IF NOT EXISTS course_major (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    major_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_major (course_id, major_id),
    KEY idx_course_major_major (major_id),
    CONSTRAINT fk_course_major_course FOREIGN KEY (course_id) REFERENCES course (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_course_major_major FOREIGN KEY (major_id) REFERENCES major (id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程专业范围';

CREATE TABLE IF NOT EXISTS student_course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    join_method VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    remark TEXT NULL,
    join_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_course (student_id, course_id),
    KEY idx_student_course_course_status (course_id, status),
    CONSTRAINT fk_student_course_student FOREIGN KEY (student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_student_course_course FOREIGN KEY (course_id) REFERENCES course (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_student_course_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DROPPED')),
    CONSTRAINT chk_student_course_method CHECK (join_method IN ('MANUAL', 'IMPORT', 'INVITE', 'AUTO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程名单';

CREATE TABLE IF NOT EXISTS scoring_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_key VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    name VARCHAR(120) NOT NULL,
    paper_method VARCHAR(64) NOT NULL DEFAULT 'PAPER_MCQS',
    formula_version VARCHAR(64) NOT NULL DEFAULT 'paper-mcqs-v1',
    standard_group_size TINYINT UNSIGNED NOT NULL DEFAULT 4,
    neutral_contribution DECIMAL(9,6)
        GENERATED ALWAYS AS (CAST(2.0 / (standard_group_size + 2) AS DECIMAL(9,6))) STORED,
    formula_assumptions JSON NOT NULL,
    feature_schema_json JSON NOT NULL,
    normalization_json JSON NULL,
    min_score DECIMAL(6,2) NOT NULL DEFAULT 0.00,
    max_score DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    course_id BIGINT NULL,
    assignment_id BIGINT NULL COMMENT 'FK is added after assignment is created',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by_admin_id BIGINT NULL,
    created_by_teacher_id BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_scoring_rule_key_version (rule_key, version_no),
    KEY idx_scoring_rule_scope (course_id, assignment_id, status),
    CONSTRAINT fk_scoring_rule_course FOREIGN KEY (course_id) REFERENCES course (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_scoring_rule_admin FOREIGN KEY (created_by_admin_id) REFERENCES admin_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_scoring_rule_teacher FOREIGN KEY (created_by_teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_scoring_rule_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT chk_scoring_rule_group_size CHECK (standard_group_size BETWEEN 1 AND 4),
    CONSTRAINT chk_scoring_rule_score_range CHECK (min_score >= 0 AND max_score <= 100 AND min_score <= max_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版本化评分规则与论文公式假设';

CREATE TABLE IF NOT EXISTS assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    course_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    deadline DATETIME(3) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    grading_criteria TEXT NULL,
    requirements TEXT NULL,
    allowed_file_types VARCHAR(255) NOT NULL DEFAULT 'docx,doc,pdf',
    max_file_size INT NOT NULL DEFAULT 10,
    allow_online_edit TINYINT(1) NOT NULL DEFAULT 0,
    allow_batch_submit TINYINT(1) NOT NULL DEFAULT 1,
    require_contribution TINYINT(1) NOT NULL DEFAULT 1,
    allow_score_view TINYINT(1) NOT NULL DEFAULT 1,
    allow_comment_view TINYINT(1) NOT NULL DEFAULT 1,
    allow_peer_view TINYINT(1) NOT NULL DEFAULT 0,
    total_score DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    enable_intelligent_scoring TINYINT(1) NOT NULL DEFAULT 1,
    standard_group_size TINYINT UNSIGNED NOT NULL DEFAULT 4,
    scoring_rule_id BIGINT NULL,
    team_rule_id BIGINT NULL COMMENT 'Legacy compatibility only',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_assignment_course_status (course_id, status),
    KEY idx_assignment_teacher_status (teacher_id, status),
    KEY idx_assignment_deadline (deadline),
    KEY idx_assignment_scoring_rule (scoring_rule_id),
    CONSTRAINT fk_assignment_course FOREIGN KEY (course_id) REFERENCES course (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_teacher FOREIGN KEY (teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_scoring_rule FOREIGN KEY (scoring_rule_id) REFERENCES scoring_rule (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_assignment_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ACTIVE', 'ENDED', 'ARCHIVED')),
    CONSTRAINT chk_assignment_score CHECK (total_score > 0 AND total_score <= 100),
    CONSTRAINT chk_assignment_group_size CHECK (standard_group_size BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队作业';

ALTER TABLE scoring_rule
    ADD CONSTRAINT fk_scoring_rule_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)
        ON UPDATE CASCADE ON DELETE CASCADE;

CREATE TABLE IF NOT EXISTS assignment_parts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    part_name VARCHAR(255) NOT NULL,
    part_description TEXT NULL,
    weight DECIMAL(7,6) NOT NULL COMMENT 'MCQS W_i, 0..1; sum per assignment is validated by service',
    sort_order INT NOT NULL DEFAULT 0,
    scoring_criteria TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_assignment_part_name (assignment_id, part_name),
    KEY idx_assignment_part_order (assignment_id, sort_order),
    CONSTRAINT fk_assignment_part_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_assignment_part_weight CHECK (weight >= 0 AND weight <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCQS 评价分支 P 与教师权重 W';

CREATE TABLE IF NOT EXISTS group_table (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    course_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    class_id INT NULL,
    major_id BIGINT NULL,
    teacher_id BIGINT NULL,
    leader_student_id BIGINT NULL,
    leader_teacher_id BIGINT NULL,
    member_count INT NOT NULL DEFAULT 0,
    current_members INT NOT NULL DEFAULT 0,
    max_members INT NOT NULL DEFAULT 6,
    status INT NOT NULL DEFAULT 1 COMMENT 'Legacy numeric lifecycle status',
    approval_status INT NOT NULL DEFAULT 0,
    team_status VARCHAR(20) NOT NULL DEFAULT 'FORMING',
    description TEXT NULL,
    join_method VARCHAR(20) NOT NULL DEFAULT 'APPROVAL',
    is_public TINYINT(1) NOT NULL DEFAULT 0,
    invite_code VARCHAR(50) NULL,
    tags VARCHAR(255) NULL COMMENT 'Legacy compatibility',
    cover_image VARCHAR(255) NULL COMMENT 'Legacy compatibility',
    activity_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    group_rating DECIMAL(5,2) NOT NULL DEFAULT 0,
    completed_assignments INT NOT NULL DEFAULT 0,
    average_score DECIMAL(6,2) NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_assignment_name (assignment_id, name),
    UNIQUE KEY uk_group_invite_code (invite_code),
    KEY idx_group_course (course_id),
    KEY idx_group_teacher (teacher_id),
    KEY idx_group_leader_student (leader_student_id),
    KEY idx_group_team_status (assignment_id, team_status),
    CONSTRAINT fk_group_course FOREIGN KEY (course_id) REFERENCES course (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_group_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_group_class FOREIGN KEY (class_id) REFERENCES `class` (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_group_major FOREIGN KEY (major_id) REFERENCES major (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_group_teacher FOREIGN KEY (teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_group_leader_student FOREIGN KEY (leader_student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_group_leader_teacher FOREIGN KEY (leader_teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_group_capacity CHECK (max_members BETWEEN 1 AND 50 AND current_members >= 0),
    CONSTRAINT chk_group_team_status CHECK (team_status IN ('FORMING', 'PENDING_CONFIRMATION', 'CONFIRMED', 'COMPLETED', 'DISSOLVED', 'ARCHIVED')),
    CONSTRAINT chk_group_join_method CHECK (join_method IN ('FREE', 'APPROVAL', 'INVITE', 'TEACHER_ASSIGNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作业小组';

CREATE TABLE IF NOT EXISTS group_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL COMMENT 'Denormalized from group_table.assignment_id for active-membership uniqueness',
    course_id BIGINT NULL COMMENT 'Legacy compatibility; authoritative course is group_table.course_id',
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
    join_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    left_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_member (group_id, student_id),
    UNIQUE KEY uk_group_member_active_assignment (assignment_id, student_id, active_guard),
    KEY idx_group_member_student_status (student_id, status),
    KEY idx_group_member_group_status (group_id, status),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES group_table (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_group_member_student FOREIGN KEY (student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_group_member_role CHECK (role IN ('LEADER', 'MEMBER')),
    CONSTRAINT chk_group_member_status CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小组成员唯一真相源';

CREATE TABLE IF NOT EXISTS group_branch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NULL,
    leader_student_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_branch_name (group_id, name),
    KEY idx_group_branch_order (group_id, sort_order),
    CONSTRAINT fk_group_branch_group FOREIGN KEY (group_id) REFERENCES group_table (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_group_branch_leader FOREIGN KEY (leader_student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_group_branch_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可选协作任务分支；不参与 MCQS W/Q 计算';

CREATE TABLE IF NOT EXISTS workload_set (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_by_teacher_id BIGINT NULL,
    created_by_student_id BIGINT NULL,
    submitted_at DATETIME(3) NULL,
    locked_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_workload_set_version (group_id, assignment_id, version_no),
    KEY idx_workload_set_status (assignment_id, status),
    CONSTRAINT fk_workload_set_group FOREIGN KEY (group_id) REFERENCES group_table (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_workload_set_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_workload_set_teacher FOREIGN KEY (created_by_teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_workload_set_student FOREIGN KEY (created_by_student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_workload_set_status CHECK (status IN ('DRAFT', 'PENDING_CONFIRMATION', 'CONFIRMED', 'LOCKED', 'SUPERSEDED')),
    CONSTRAINT chk_workload_set_version CHECK (version_no >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一次小组 W/Q 工作量分配版本';

CREATE TABLE IF NOT EXISTS workload_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workload_set_id BIGINT NOT NULL,
    assignment_part_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    branch_id BIGINT NULL COMMENT 'Optional collaboration branch; excluded from MCQS dimensions',
    share DECIMAL(7,6) NOT NULL COMMENT 'MCQS Q_ij, 0..1; sum per set and part is validated by service',
    task_title VARCHAR(200) NULL,
    task_description TEXT NULL,
    planned_hours DECIMAL(8,2) NULL,
    actual_hours DECIMAL(8,2) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    evidence_json TEXT NULL COMMENT 'Human evidence text; name retained for API compatibility',
    sort_order INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_workload_item_q (workload_set_id, assignment_part_id, student_id),
    KEY idx_workload_item_student (student_id, status),
    KEY idx_workload_item_part (assignment_part_id),
    KEY idx_workload_item_branch (branch_id),
    CONSTRAINT fk_workload_item_set FOREIGN KEY (workload_set_id) REFERENCES workload_set (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_workload_item_part FOREIGN KEY (assignment_part_id) REFERENCES assignment_parts (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_workload_item_student FOREIGN KEY (student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_workload_item_branch FOREIGN KEY (branch_id) REFERENCES group_branch (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_workload_item_share CHECK (share >= 0 AND share <= 1),
    CONSTRAINT chk_workload_item_hours CHECK ((planned_hours IS NULL OR planned_hours >= 0) AND (actual_hours IS NULL OR actual_hours >= 0)),
    CONSTRAINT chk_workload_item_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCQS 每个评价分支与成员的 Q 份额';

CREATE TABLE IF NOT EXISTS workload_confirmation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workload_set_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comment VARCHAR(1000) NULL,
    confirmed_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_workload_confirmation (workload_set_id, student_id),
    KEY idx_workload_confirmation_status (workload_set_id, status),
    CONSTRAINT fk_workload_confirmation_set FOREIGN KEY (workload_set_id) REFERENCES workload_set (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_workload_confirmation_student FOREIGN KEY (student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_workload_confirmation_status CHECK (status IN ('PENDING', 'CONFIRMED', 'OBJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成员对 W/Q 分配版本的确认';

CREATE TABLE IF NOT EXISTS submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    attempt_no SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    is_latest TINYINT(1) NOT NULL DEFAULT 1,
    latest_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_latest = 1 THEN 1 ELSE NULL END) STORED,
    submitter_id BIGINT NULL,
    content LONGTEXT NULL,
    online_content LONGTEXT NULL,
    file_path VARCHAR(1024) NULL,
    artifact_json JSON NULL,
    file_count INT NOT NULL DEFAULT 0,
    submit_note TEXT NULL,
    submit_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    teacher_score DECIMAL(6,2) NULL,
    teacher_comment TEXT NULL,
    graded_by BIGINT NULL,
    graded_time DATETIME(3) NULL,
    algorithm_score DECIMAL(6,2) NULL COMMENT 'Legacy compatibility; score_result is authoritative',
    has_contribution TINYINT(1) NOT NULL DEFAULT 0,
    contribution_scores LONGTEXT NULL COMMENT 'Legacy JSON snapshot only',
    group_member_count INT NOT NULL DEFAULT 0,
    is_final_score_calculated TINYINT(1) NOT NULL DEFAULT 0,
    is_score_published TINYINT(1) NOT NULL DEFAULT 0,
    score_publish_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_submission_latest (assignment_id, group_id, latest_guard),
    KEY idx_submission_attempt (assignment_id, group_id, attempt_no),
    KEY idx_submission_status_time (status, submit_time),
    KEY idx_submission_submitter (submitter_id),
    CONSTRAINT fk_submission_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_submission_group FOREIGN KEY (group_id) REFERENCES group_table (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_submission_submitter FOREIGN KEY (submitter_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_submission_grader FOREIGN KEY (graded_by) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_submission_attempt CHECK (attempt_no >= 1),
    CONSTRAINT chk_submission_latest CHECK (is_latest IN (0, 1)),
    CONSTRAINT chk_submission_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'GRADED', 'ARCHIVED')),
    CONSTRAINT chk_submission_teacher_score CHECK (teacher_score IS NULL OR (teacher_score >= 0 AND teacher_score <= 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小组作业提交';

CREATE TABLE IF NOT EXISTS peer_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    submission_id BIGINT NULL,
    reviewer_student_id BIGINT NULL COMMENT 'NULL is allowed only for imported legacy records',
    reviewee_student_id BIGINT NOT NULL,
    overall_score DECIMAL(6,2) NOT NULL,
    reason VARCHAR(1000) NULL,
    evaluation_type VARCHAR(30) NOT NULL DEFAULT 'PEER',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    legacy_source_id BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_peer_evaluation (assignment_id, group_id, reviewer_student_id, reviewee_student_id, evaluation_type),
    UNIQUE KEY uk_peer_evaluation_legacy (legacy_source_id),
    KEY idx_peer_reviewee (assignment_id, reviewee_student_id),
    CONSTRAINT fk_peer_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_peer_group FOREIGN KEY (group_id) REFERENCES group_table (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_peer_submission FOREIGN KEY (submission_id) REFERENCES submission (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_peer_reviewer FOREIGN KEY (reviewer_student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_peer_reviewee FOREIGN KEY (reviewee_student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_peer_score CHECK (overall_score >= 0 AND overall_score <= 100),
    CONSTRAINT chk_peer_type CHECK (evaluation_type IN ('SELF', 'PEER', 'LEADER', 'TEACHER', 'LEGACY')),
    CONSTRAINT chk_peer_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'INVALIDATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='补充评价证据；MCQS 核心贡献仍由 W/Q 计算';

CREATE TABLE IF NOT EXISTS model_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    model_key VARCHAR(64) NOT NULL,
    version VARCHAR(64) NOT NULL,
    algorithm VARCHAR(30) NOT NULL DEFAULT 'XGBOOST',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    active_model_key VARCHAR(64) GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN model_key ELSE NULL END) STORED,
    artifact_uri VARCHAR(1024) NULL,
    artifact_sha256 CHAR(64) NULL,
    feature_schema_json JSON NOT NULL,
    hyperparameters_json JSON NULL,
    metrics_json JSON NULL,
    trained_at DATETIME(3) NULL,
    activated_at DATETIME(3) NULL,
    created_by_admin_id BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_key_version (model_key, version),
    UNIQUE KEY uk_model_one_active (active_model_key),
    KEY idx_model_status (model_key, status),
    CONSTRAINT fk_model_admin FOREIGN KEY (created_by_admin_id) REFERENCES admin_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_model_algorithm CHECK (algorithm IN ('XGBOOST', 'RULE', 'LEGACY')),
    CONSTRAINT chk_model_status CHECK (status IN ('DRAFT', 'VALIDATED', 'ACTIVE', 'RETIRED')),
    CONSTRAINT chk_model_artifact_hash CHECK (artifact_sha256 IS NULL OR artifact_sha256 REGEXP '^[0-9a-fA-F]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评分模型版本与可复现元数据';

CREATE TABLE IF NOT EXISTS scoring_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_no VARCHAR(80) NOT NULL,
    assignment_id BIGINT NOT NULL,
    group_id BIGINT NULL COMMENT 'NULL only for legacy assignment-level imports',
    submission_id BIGINT NULL COMMENT 'NULL only for legacy imports',
    scoring_rule_id BIGINT NOT NULL,
    model_version_id BIGINT NULL COMMENT 'NULL means deterministic rule fallback without an active XGBoost model',
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    standard_group_size TINYINT UNSIGNED NOT NULL DEFAULT 4,
    actual_group_size TINYINT UNSIGNED NULL,
    group_score DECIMAL(6,2) NULL,
    input_hash CHAR(64) NULL,
    error_message VARCHAR(2000) NULL,
    triggered_by_teacher_id BIGINT NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_scoring_run_no (run_no),
    KEY idx_scoring_run_assignment_status (assignment_id, status),
    KEY idx_scoring_run_submission (submission_id),
    KEY idx_scoring_run_group (group_id),
    CONSTRAINT fk_scoring_run_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_scoring_run_group FOREIGN KEY (group_id) REFERENCES group_table (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_scoring_run_submission FOREIGN KEY (submission_id) REFERENCES submission (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_scoring_run_rule FOREIGN KEY (scoring_rule_id) REFERENCES scoring_rule (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_scoring_run_model FOREIGN KEY (model_version_id) REFERENCES model_version (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_scoring_run_teacher FOREIGN KEY (triggered_by_teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_scoring_run_status CHECK (status IN ('CREATED', 'MODEL_COMPLETE', 'DEGRADED', 'PENDING_REVIEW', 'APPROVED', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_scoring_run_sizes CHECK (standard_group_size BETWEEN 1 AND 4 AND (actual_group_size IS NULL OR actual_group_size BETWEEN 1 AND standard_group_size + 1)),
    CONSTRAINT chk_scoring_run_hash CHECK (input_hash IS NULL OR input_hash REGEXP '^[0-9a-fA-F]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一次可审计的评分运行';

CREATE TABLE IF NOT EXISTS scoring_input_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scoring_run_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    contribution DECIMAL(9,6) NULL COMMENT 'MCQS c_i = Q_i * W^T, 0..1',
    standard_group_size TINYINT UNSIGNED NULL COMMENT 'Optional projection; authoritative value is also retained in features_json',
    actual_group_size TINYINT UNSIGNED NULL COMMENT 'Optional projection; authoritative value is also retained in features_json',
    group_score DECIMAL(6,2) NULL COMMENT 'Optional projection; authoritative value is also retained in features_json',
    feature_schema_version VARCHAR(64) NOT NULL,
    features_json JSON NOT NULL,
    source_snapshot_json JSON NOT NULL,
    feature_hash CHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_scoring_snapshot_student (scoring_run_id, student_id),
    KEY idx_scoring_snapshot_hash (scoring_run_id, feature_hash),
    KEY idx_scoring_snapshot_student (student_id),
    CONSTRAINT fk_scoring_snapshot_run FOREIGN KEY (scoring_run_id) REFERENCES scoring_run (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_scoring_snapshot_student FOREIGN KEY (student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_scoring_snapshot_contribution CHECK (contribution IS NULL OR (contribution >= 0 AND contribution <= 1)),
    CONSTRAINT chk_scoring_snapshot_hash CHECK (feature_hash REGEXP '^[0-9a-fA-F]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变评分输入快照';

CREATE TABLE IF NOT EXISTS score_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scoring_run_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    contribution DECIMAL(9,6) NULL,
    group_score DECIMAL(6,2) NOT NULL,
    rule_score DECIMAL(6,2) NOT NULL,
    model_score DECIMAL(6,2) NULL,
    model_rule_delta DECIMAL(6,2) NULL,
    anomaly_flag TINYINT(1) NOT NULL DEFAULT 0,
    anomaly_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    anomaly_resolution VARCHAR(2000) NULL,
    anomaly_resolved_by_admin_id BIGINT NULL,
    anomaly_resolved_at DATETIME(3) NULL,
    manual_adjustment DECIMAL(6,2) NOT NULL DEFAULT 0.00,
    final_score DECIMAL(6,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    explanation_json JSON NOT NULL,
    review_reason VARCHAR(2000) NULL,
    reviewed_by_teacher_id BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    published_at DATETIME(3) NULL,
    legacy_final_score_id BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_score_result_student (scoring_run_id, student_id),
    UNIQUE KEY uk_score_result_legacy (legacy_final_score_id),
    KEY idx_score_result_student_status (student_id, status),
    KEY idx_score_result_anomaly (anomaly_flag, anomaly_status),
    CONSTRAINT fk_score_result_run FOREIGN KEY (scoring_run_id) REFERENCES scoring_run (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_score_result_student FOREIGN KEY (student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_score_result_reviewer FOREIGN KEY (reviewed_by_teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_score_result_anomaly_admin FOREIGN KEY (anomaly_resolved_by_admin_id) REFERENCES admin_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_score_result_contribution CHECK (contribution IS NULL OR (contribution >= 0 AND contribution <= 1)),
    CONSTRAINT chk_score_result_scores CHECK (group_score BETWEEN 0 AND 100 AND rule_score BETWEEN 0 AND 100 AND final_score BETWEEN 0 AND 100 AND (model_score IS NULL OR model_score BETWEEN 0 AND 100)),
    CONSTRAINT chk_score_result_anomaly CHECK (anomaly_flag IN (0, 1)),
    CONSTRAINT chk_score_result_anomaly_status CHECK (anomaly_status IN ('PENDING', 'RESOLVED')),
    CONSTRAINT chk_score_result_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REVIEWED', 'APPROVED', 'PUBLISHED', 'LEGACY_IMPORTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人差异化评分结果';

CREATE TABLE IF NOT EXISTS individual_score (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    score_result_id BIGINT NULL,
    teacher_score DECIMAL(6,2) NOT NULL,
    contribution_ratio DECIMAL(7,4) NOT NULL,
    individual_final_score DECIMAL(6,2) NOT NULL,
    difficulty_factor DECIMAL(7,4) NOT NULL DEFAULT 1.0000,
    collaboration_factor DECIMAL(7,4) NOT NULL DEFAULT 1.0000,
    calculated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    calculated_by BIGINT NULL,
    is_published TINYINT(1) NOT NULL DEFAULT 0,
    publish_time DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_individual_submission_student (submission_id, student_id),
    UNIQUE KEY uk_individual_score_result (score_result_id),
    KEY idx_individual_student (student_id),
    CONSTRAINT fk_individual_submission FOREIGN KEY (submission_id) REFERENCES submission (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_individual_student FOREIGN KEY (student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_individual_result FOREIGN KEY (score_result_id) REFERENCES score_result (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_individual_calculator FOREIGN KEY (calculated_by) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_individual_contribution CHECK (contribution_ratio >= 0 AND contribution_ratio <= 100),
    CONSTRAINT chk_individual_published CHECK (is_published IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='兼容个人成绩投影';

CREATE TABLE IF NOT EXISTS final_score (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    score_result_id BIGINT NULL,
    type VARCHAR(30) NOT NULL DEFAULT 'ASSIGNMENT',
    weight DECIMAL(7,4) NOT NULL DEFAULT 1.0000,
    score DECIMAL(6,2) NOT NULL,
    graded_time DATETIME(3) NULL,
    graded_by BIGINT NULL,
    comment TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_final_student_assignment (student_id, assignment_id),
    UNIQUE KEY uk_final_score_result (score_result_id),
    KEY idx_final_assignment (assignment_id),
    CONSTRAINT fk_final_student FOREIGN KEY (student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_final_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_final_course FOREIGN KEY (course_id) REFERENCES course (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_final_result FOREIGN KEY (score_result_id) REFERENCES score_result (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_final_grader FOREIGN KEY (graded_by) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_final_score CHECK (score >= 0 AND score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已发布成绩投影';

CREATE TABLE IF NOT EXISTS score_appeal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    score_result_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    evidence_json JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    resolution VARCHAR(2000) NULL,
    resolved_by_teacher_id BIGINT NULL,
    submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_appeal_status (status, submitted_at),
    KEY idx_appeal_student (student_id, status),
    CONSTRAINT fk_appeal_result FOREIGN KEY (score_result_id) REFERENCES score_result (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_appeal_student FOREIGN KEY (student_id) REFERENCES student_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_appeal_teacher FOREIGN KEY (resolved_by_teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_appeal_status CHECK (status IN ('SUBMITTED', 'PROCESSING', 'ACCEPTED', 'REJECTED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成绩申诉';

CREATE TABLE IF NOT EXISTS score_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    score_result_id BIGINT NOT NULL,
    score_appeal_id BIGINT NOT NULL,
    revised_by_teacher_id BIGINT NOT NULL,
    previous_score DECIMAL(6,2) NOT NULL,
    revised_score DECIMAL(6,2) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_score_revision_appeal (score_appeal_id),
    KEY idx_score_revision_result_created (score_result_id, created_at, id),
    KEY idx_score_revision_teacher_created (revised_by_teacher_id, created_at),
    CONSTRAINT fk_score_revision_result FOREIGN KEY (score_result_id) REFERENCES score_result (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_score_revision_appeal FOREIGN KEY (score_appeal_id) REFERENCES score_appeal (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_score_revision_teacher FOREIGN KEY (revised_by_teacher_id) REFERENCES teacher_users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_score_revision_scores CHECK (previous_score BETWEEN 0 AND 100 AND revised_score BETWEEN 0 AND 100),
    CONSTRAINT chk_score_revision_changed CHECK (previous_score <> revised_score),
    CONSTRAINT chk_score_revision_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Append-only score revision audit';

CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    token_version INT NOT NULL DEFAULT 0,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_used_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user (role, user_id, revoked_at),
    KEY idx_refresh_expiry (expires_at),
    CONSTRAINT chk_refresh_role CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT')),
    CONSTRAINT chk_refresh_hash CHECK (token_hash REGEXP '^[0-9a-fA-F]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仅保存哈希的刷新令牌';

CREATE TABLE IF NOT EXISTS import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_no VARCHAR(80) NOT NULL,
    import_type VARCHAR(40) NOT NULL,
    file_name VARCHAR(255) NULL,
    file_sha256 CHAR(64) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    total_rows INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    failure_rows INT NOT NULL DEFAULT 0,
    error_report_json JSON NULL,
    created_by_admin_id BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_batch_no (batch_no),
    KEY idx_import_status (status, created_at),
    CONSTRAINT fk_import_admin FOREIGN KEY (created_by_admin_id) REFERENCES admin_users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_import_status CHECK (status IN ('CREATED', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    CONSTRAINT chk_import_counts CHECK (total_rows >= 0 AND success_rows >= 0 AND failure_rows >= 0),
    CONSTRAINT chk_import_hash CHECK (file_sha256 IS NULL OR file_sha256 REGEXP '^[0-9a-fA-F]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批量导入审计';

CREATE TABLE IF NOT EXISTS audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(80) NOT NULL,
    actor_role VARCHAR(20) NULL,
    actor_id BIGINT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    request_id VARCHAR(80) NULL,
    detail_json JSON NOT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(512) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_aggregate (aggregate_type, aggregate_id, created_at),
    KEY idx_audit_actor (actor_role, actor_id, created_at),
    KEY idx_audit_event_time (event_type, created_at),
    KEY idx_audit_request (request_id),
    CONSTRAINT chk_audit_actor_role CHECK (actor_role IS NULL OR actor_role IN ('SYSTEM', 'ADMIN', 'TEACHER', 'STUDENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变业务审计事件';

CREATE TABLE IF NOT EXISTS legacy_record_archive (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_table VARCHAR(64) NOT NULL,
    source_pk VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    migration_batch_no VARCHAR(80) NOT NULL,
    migrated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_legacy_record (source_table, source_pk),
    KEY idx_legacy_batch (migration_batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仅用于无损保存无法直接映射的旧记录';
