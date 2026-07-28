-- Groupwork Flyway V2: legacy compatibility plus production core schema
-- Target: MySQL 8.0.29+
-- New database: apply after V1.
-- Existing pre-Flyway database: baseline at version 1, then apply V2 once.
-- Do not rerun manually after a successful Flyway migration.

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

-- user_account_view is created after legacy account columns are normalized.

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

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'scoring_rule'
             AND constraint_name = 'fk_scoring_rule_assignment' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE scoring_rule ADD CONSTRAINT fk_scoring_rule_assignment FOREIGN KEY (assignment_id) REFERENCES assignment(id) ON UPDATE CASCADE ON DELETE CASCADE'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

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



-- Legacy-to-core compatibility phase. All statements below are safe on an empty
-- V2 database and are executed exactly once by Flyway on a legacy database.
SET @gw_migration_batch = 'V2_20260715';
SET @gw_disabled_password_hash = '$2a$12$mcvOYoEMYlcdx9K9HGqUvO0aGris4ScXxG8jDz3qXnA9JEZG90Q0C';
SET foreign_key_checks = 0;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'department' AND column_name = 'status'),
    'SELECT 1',
    'ALTER TABLE department ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'''
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE department
SET status = CASE WHEN UPPER(COALESCE(status, 'ACTIVE')) = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END,
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
ALTER TABLE department
    MODIFY COLUMN name VARCHAR(100) NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN teacher_count INT NOT NULL DEFAULT 0,
    MODIFY COLUMN student_count INT NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'major' AND column_name = 'status'),
    'SELECT 1',
    'ALTER TABLE major ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'''
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE major
SET status = CASE WHEN UPPER(COALESCE(status, 'ACTIVE')) = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END,
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
ALTER TABLE major
    MODIFY COLUMN name VARCHAR(100) NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN student_count INT NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'class' AND column_name = 'status'),
    'SELECT 1',
    'ALTER TABLE class ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'''
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE class
SET status = CASE WHEN UPPER(COALESCE(status, 'ACTIVE')) = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END,
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
ALTER TABLE class
    MODIFY COLUMN name VARCHAR(100) NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN student_count INT NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'admin_users' AND column_name = 'display_name'),
    'SELECT 1',
    'ALTER TABLE admin_users ADD COLUMN display_name VARCHAR(100) NULL AFTER password'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'admin_users' AND column_name = 'status'),
    'SELECT 1',
    'ALTER TABLE admin_users ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1 AFTER role'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'admin_users' AND column_name = 'must_change_password'),
    'SELECT 1',
    'ALTER TABLE admin_users ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER status'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'admin_users' AND column_name = 'token_version'),
    'SELECT 1',
    'ALTER TABLE admin_users ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER must_change_password'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'admin_users' AND column_name = 'last_login_time'),
    'SELECT 1',
    'ALTER TABLE admin_users ADD COLUMN last_login_time DATETIME(3) NULL AFTER token_version'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE admin_users
SET display_name = COALESCE(NULLIF(display_name, ''), username),
    role = 'ROLE_ADMIN',
    status = CASE WHEN status = 0 THEN 0 ELSE 1 END,
    must_change_password = CASE WHEN must_change_password = 1 THEN 1 ELSE 0 END,
    token_version = GREATEST(COALESCE(token_version, 0), 0),
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
UPDATE admin_users
SET password = @gw_disabled_password_hash, status = 0, must_change_password = 1
WHERE CHAR_LENGTH(password) <> 60 OR LEFT(password, 4) NOT IN ('$2a$', '$2b$', '$2y$');
ALTER TABLE admin_users
    MODIFY COLUMN username VARCHAR(50) NOT NULL,
    MODIFY COLUMN password VARCHAR(100) NOT NULL COMMENT 'BCrypt hash only',
    MODIFY COLUMN phone VARCHAR(20) NULL,
    MODIFY COLUMN admin_level INT NOT NULL DEFAULT 1,
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ROLE_ADMIN',
    MODIFY COLUMN status TINYINT(1) NOT NULL DEFAULT 1,
    MODIFY COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN token_version INT NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'teacher_users' AND column_name = 'role'),
    'SELECT 1',
    'ALTER TABLE teacher_users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT ''ROLE_TEACHER'' AFTER department_id'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'teacher_users' AND column_name = 'must_change_password'),
    'SELECT 1',
    'ALTER TABLE teacher_users ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER status'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'teacher_users' AND column_name = 'token_version'),
    'SELECT 1',
    'ALTER TABLE teacher_users ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER must_change_password'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'teacher_users' AND column_name = 'last_login_time'),
    'SELECT 1',
    'ALTER TABLE teacher_users ADD COLUMN last_login_time DATETIME(3) NULL AFTER token_version'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE teacher_users
SET teacher_user_id = COALESCE(NULLIF(teacher_user_id, ''), CONCAT('LEGACY-T-', id)),
    role = 'ROLE_TEACHER',
    status = CASE WHEN status = 0 THEN 0 ELSE 1 END,
    must_change_password = CASE WHEN must_change_password = 1 THEN 1 ELSE 0 END,
    token_version = GREATEST(COALESCE(token_version, 0), 0),
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
UPDATE teacher_users
SET password = @gw_disabled_password_hash, status = 0, must_change_password = 1
WHERE CHAR_LENGTH(password) <> 60 OR LEFT(password, 4) NOT IN ('$2a$', '$2b$', '$2y$');
ALTER TABLE teacher_users
    MODIFY COLUMN teacher_user_id VARCHAR(20) NOT NULL,
    MODIFY COLUMN password VARCHAR(100) NOT NULL COMMENT 'BCrypt hash only',
    MODIFY COLUMN name VARCHAR(100) NULL,
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ROLE_TEACHER',
    MODIFY COLUMN status TINYINT(1) NOT NULL DEFAULT 1,
    MODIFY COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN token_version INT NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'student_users' AND column_name = 'role'),
    'SELECT 1',
    'ALTER TABLE student_users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT ''ROLE_STUDENT'' AFTER grade'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'student_users' AND column_name = 'must_change_password'),
    'SELECT 1',
    'ALTER TABLE student_users ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER status'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'student_users' AND column_name = 'token_version'),
    'SELECT 1',
    'ALTER TABLE student_users ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER must_change_password'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'student_users' AND column_name = 'last_login_time'),
    'SELECT 1',
    'ALTER TABLE student_users ADD COLUMN last_login_time DATETIME(3) NULL AFTER token_version'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE student_users
SET student_user_id = COALESCE(NULLIF(student_user_id, ''), CONCAT('LEGACY-S-', id)),
    role = 'ROLE_STUDENT',
    status = CASE WHEN status = 0 THEN 0 ELSE 1 END,
    must_change_password = CASE WHEN must_change_password = 1 THEN 1 ELSE 0 END,
    token_version = GREATEST(COALESCE(token_version, 0), 0),
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
UPDATE student_users
SET password = @gw_disabled_password_hash, status = 0, must_change_password = 1
WHERE CHAR_LENGTH(password) <> 60 OR LEFT(password, 4) NOT IN ('$2a$', '$2b$', '$2y$');
ALTER TABLE student_users
    MODIFY COLUMN student_user_id VARCHAR(20) NOT NULL,
    MODIFY COLUMN password VARCHAR(100) NOT NULL COMMENT 'BCrypt hash only',
    MODIFY COLUMN name VARCHAR(100) NULL,
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ROLE_STUDENT',
    MODIFY COLUMN status TINYINT(1) NOT NULL DEFAULT 1,
    MODIFY COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN token_version INT NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'refresh_token' AND column_name = 'token_version'),
    'SELECT 1',
    'ALTER TABLE refresh_token ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER role'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE refresh_token
SET token_version = GREATEST(COALESCE(token_version, 0), 0);
ALTER TABLE refresh_token
    MODIFY COLUMN token_version INT NOT NULL DEFAULT 0;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'course' AND column_name = 'code'),
    'SELECT 1',
    'ALTER TABLE course ADD COLUMN code VARCHAR(50) NULL AFTER id'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'course' AND column_name = 'term'),
    'SELECT 1',
    'ALTER TABLE course ADD COLUMN term VARCHAR(32) NULL AFTER major_id'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'course' AND column_name = 'status'),
    'SELECT 1',
    'ALTER TABLE course ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'' AFTER end_date'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
INSERT IGNORE INTO legacy_record_archive(source_table, source_pk, payload_json, migration_batch_no)
SELECT 'course_invalid_reference', CAST(c.id AS CHAR),
       JSON_OBJECT('id', c.id, 'name', c.name, 'teacherId', c.teacher_id, 'majorId', c.major_id),
       @gw_migration_batch
FROM course c
LEFT JOIN teacher_users t ON t.id = c.teacher_id
LEFT JOIN major m ON m.id = c.major_id
WHERE (c.teacher_id IS NOT NULL AND t.id IS NULL) OR (c.major_id IS NOT NULL AND m.id IS NULL);
UPDATE course c LEFT JOIN teacher_users t ON t.id = c.teacher_id
SET c.teacher_id = NULL WHERE c.teacher_id IS NOT NULL AND t.id IS NULL;
UPDATE course c LEFT JOIN major m ON m.id = c.major_id
SET c.major_id = NULL WHERE c.major_id IS NOT NULL AND m.id IS NULL;
UPDATE course
SET code = COALESCE(NULLIF(code, ''), CONCAT('LEGACY-C-', LPAD(id, 8, '0'))),
    status = CASE
        WHEN UPPER(COALESCE(status, 'ACTIVE')) IN ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED')
            THEN UPPER(COALESCE(status, 'ACTIVE'))
        ELSE 'ACTIVE'
    END,
    student_count = GREATEST(COALESCE(student_count, 0), 0),
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
ALTER TABLE course
    MODIFY COLUMN code VARCHAR(50) NULL,
    MODIFY COLUMN name VARCHAR(100) NOT NULL,
    MODIFY COLUMN term VARCHAR(32) NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN student_count INT NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'student_course' AND column_name = 'join_time'),
    'SELECT 1',
    'ALTER TABLE student_course ADD COLUMN join_time DATETIME(3) NULL AFTER remark'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE student_course
SET status = CASE WHEN UPPER(COALESCE(status, 'ACTIVE')) IN ('ACTIVE', 'INACTIVE', 'DROPPED')
                  THEN UPPER(COALESCE(status, 'ACTIVE')) ELSE 'ACTIVE' END,
    join_method = CASE WHEN UPPER(COALESCE(join_method, 'MANUAL')) IN ('MANUAL', 'IMPORT', 'INVITE', 'AUTO')
                       THEN UPPER(COALESCE(join_method, 'MANUAL')) ELSE 'MANUAL' END,
    join_time = COALESCE(join_time, create_time, CURRENT_TIMESTAMP(3)),
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
ALTER TABLE student_course
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN join_method VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    MODIFY COLUMN join_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'assignment' AND column_name = 'standard_group_size'),
    'SELECT 1',
    'ALTER TABLE assignment ADD COLUMN standard_group_size TINYINT UNSIGNED NOT NULL DEFAULT 4 AFTER enable_intelligent_scoring'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'assignment' AND column_name = 'scoring_rule_id'),
    'SELECT 1',
    'ALTER TABLE assignment ADD COLUMN scoring_rule_id BIGINT NULL AFTER standard_group_size'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'assignment' AND column_name = 'update_time'),
    'SELECT 1',
    'ALTER TABLE assignment ADD COLUMN update_time DATETIME(3) NULL AFTER create_time'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'assignment' AND column_name = 'created_at'),
    'SELECT 1',
    'ALTER TABLE assignment ADD COLUMN created_at DATETIME(3) NULL AFTER update_time'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'assignment' AND column_name = 'updated_at'),
    'SELECT 1',
    'ALTER TABLE assignment ADD COLUMN updated_at DATETIME(3) NULL AFTER created_at'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE assignment
SET status = CASE
        WHEN UPPER(COALESCE(status, 'DRAFT')) = 'ACTIVE' THEN 'PUBLISHED'
        WHEN UPPER(COALESCE(status, 'DRAFT')) = 'ENDED' THEN 'CLOSED'
        WHEN UPPER(COALESCE(status, 'DRAFT')) IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')
            THEN UPPER(COALESCE(status, 'DRAFT'))
        ELSE 'DRAFT'
    END,
    total_score = CASE WHEN total_score IS NULL OR total_score <= 0 THEN 100.00 ELSE LEAST(total_score, 100.00) END,
    standard_group_size = CASE WHEN standard_group_size BETWEEN 1 AND 4 THEN standard_group_size ELSE 4 END,
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3)),
    created_at = COALESCE(created_at, create_time, CURRENT_TIMESTAMP(3)),
    updated_at = COALESCE(updated_at, update_time, created_at, CURRENT_TIMESTAMP(3));
ALTER TABLE assignment
    MODIFY COLUMN course_id BIGINT NOT NULL,
    MODIFY COLUMN teacher_id BIGINT NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    MODIFY COLUMN total_score DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    MODIFY COLUMN standard_group_size TINYINT UNSIGNED NOT NULL DEFAULT 4,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

UPDATE assignment_parts
SET weight = CASE WHEN weight > 1 THEN weight / 100 ELSE GREATEST(weight, 0) END,
    sort_order = COALESCE(sort_order, 0),
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
ALTER TABLE assignment_parts
    MODIFY COLUMN weight DECIMAL(7,6) NOT NULL,
    MODIFY COLUMN sort_order INT NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

INSERT IGNORE INTO legacy_record_archive(source_table, source_pk, payload_json, migration_batch_no)
SELECT 'group_invalid_reference', CAST(g.id AS CHAR),
       JSON_OBJECT('id', g.id, 'name', g.name, 'teacherId', g.teacher_id),
       @gw_migration_batch
FROM group_table g LEFT JOIN teacher_users t ON t.id = g.teacher_id
WHERE g.teacher_id IS NOT NULL AND t.id IS NULL;
UPDATE group_table g LEFT JOIN teacher_users t ON t.id = g.teacher_id
SET g.teacher_id = NULL WHERE g.teacher_id IS NOT NULL AND t.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS gw_duplicate_group_name;
CREATE TEMPORARY TABLE gw_duplicate_group_name AS
SELECT id, assignment_id, name,
       ROW_NUMBER() OVER (PARTITION BY assignment_id, name ORDER BY id) AS duplicate_no
FROM group_table;
INSERT IGNORE INTO legacy_record_archive(source_table, source_pk, payload_json, migration_batch_no)
SELECT 'group_duplicate_name', CAST(id AS CHAR),
       JSON_OBJECT('id', id, 'assignmentId', assignment_id, 'originalName', name),
       @gw_migration_batch
FROM gw_duplicate_group_name WHERE duplicate_no > 1;
UPDATE group_table g JOIN gw_duplicate_group_name d ON d.id = g.id
SET g.name = CONCAT(LEFT(g.name, 72), ' [legacy-', g.id, ']')
WHERE d.duplicate_no > 1;
DROP TEMPORARY TABLE gw_duplicate_group_name;

UPDATE group_table
SET team_status = CASE WHEN UPPER(COALESCE(team_status, 'FORMING')) IN
        ('FORMING', 'PENDING_CONFIRMATION', 'CONFIRMED', 'COMPLETED', 'DISSOLVED', 'ARCHIVED')
        THEN UPPER(COALESCE(team_status, 'FORMING')) ELSE 'FORMING' END,
    join_method = CASE WHEN UPPER(COALESCE(join_method, 'APPROVAL')) IN
        ('FREE', 'APPROVAL', 'INVITE', 'TEACHER_ASSIGNED')
        THEN UPPER(COALESCE(join_method, 'APPROVAL')) ELSE 'APPROVAL' END,
    max_members = LEAST(GREATEST(COALESCE(max_members, 6), 1), 50),
    current_members = GREATEST(COALESCE(current_members, 0), 0),
    member_count = GREATEST(COALESCE(member_count, 0), 0),
    status = COALESCE(status, 1),
    approval_status = COALESCE(approval_status, 0),
    is_public = CASE WHEN is_public = 1 THEN 1 ELSE 0 END,
    create_time = COALESCE(create_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, create_time, CURRENT_TIMESTAMP(3));
ALTER TABLE group_table
    MODIFY COLUMN course_id BIGINT NOT NULL,
    MODIFY COLUMN assignment_id BIGINT NOT NULL,
    MODIFY COLUMN member_count INT NOT NULL DEFAULT 0,
    MODIFY COLUMN current_members INT NOT NULL DEFAULT 0,
    MODIFY COLUMN max_members INT NOT NULL DEFAULT 6,
    MODIFY COLUMN status INT NOT NULL DEFAULT 1,
    MODIFY COLUMN approval_status INT NOT NULL DEFAULT 0,
    MODIFY COLUMN team_status VARCHAR(20) NOT NULL DEFAULT 'FORMING',
    MODIFY COLUMN join_method VARCHAR(20) NOT NULL DEFAULT 'APPROVAL',
    MODIFY COLUMN is_public TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'group_member' AND column_name = 'joined_at'),
    'SELECT 1',
    'ALTER TABLE group_member ADD COLUMN joined_at DATETIME(3) NULL AFTER join_time'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'group_member' AND column_name = 'left_time'),
    'SELECT 1',
    'ALTER TABLE group_member ADD COLUMN left_time DATETIME(3) NULL AFTER joined_at'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'group_member' AND column_name = 'create_time'),
    'SELECT 1',
    'ALTER TABLE group_member ADD COLUMN create_time DATETIME(3) NULL AFTER left_time'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'group_member' AND column_name = 'update_time'),
    'SELECT 1',
    'ALTER TABLE group_member ADD COLUMN update_time DATETIME(3) NULL AFTER create_time'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'group_member' AND column_name = 'active_guard'),
    'SELECT 1',
    'ALTER TABLE group_member ADD COLUMN active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status = ''ACTIVE'' THEN 1 ELSE NULL END) STORED AFTER status'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema = DATABASE() AND table_name = 'team_relation'),
    'INSERT INTO group_member(group_id,student_id,assignment_id,course_id,role,status,join_time,joined_at,create_time,update_time)
     SELECT tr.group_id,tr.student_id,g.assignment_id,g.course_id,
            CASE WHEN UPPER(COALESCE(tr.role,''MEMBER''))=''LEADER'' THEN ''LEADER'' ELSE ''MEMBER'' END,
            CASE WHEN UPPER(COALESCE(tr.status,''ACTIVE''))=''ACTIVE'' THEN ''ACTIVE'' ELSE ''LEFT'' END,
            COALESCE(tr.join_time,CURRENT_TIMESTAMP(3)),COALESCE(tr.join_time,CURRENT_TIMESTAMP(3)),
            COALESCE(tr.join_time,CURRENT_TIMESTAMP(3)),COALESCE(tr.join_time,CURRENT_TIMESTAMP(3))
     FROM team_relation tr
     JOIN group_table g ON g.id=tr.group_id
     JOIN student_users s ON s.id=tr.student_id
     WHERE NOT EXISTS (SELECT 1 FROM group_member gm
                       WHERE gm.group_id=tr.group_id AND gm.student_id=tr.student_id)',
    'SELECT 1'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

UPDATE group_member gm JOIN group_table g ON g.id = gm.group_id
SET gm.assignment_id = g.assignment_id,
    gm.course_id = g.course_id,
    gm.role = CASE WHEN UPPER(COALESCE(gm.role, 'MEMBER')) = 'LEADER' THEN 'LEADER' ELSE 'MEMBER' END,
    gm.status = CASE
        WHEN UPPER(COALESCE(gm.status, 'ACTIVE')) = 'ACTIVE' THEN 'ACTIVE'
        WHEN UPPER(COALESCE(gm.status, 'ACTIVE')) IN ('LEFT', 'INACTIVE') THEN 'LEFT'
        ELSE 'REMOVED'
    END,
    gm.join_time = COALESCE(gm.join_time, CURRENT_TIMESTAMP(3)),
    gm.joined_at = COALESCE(gm.joined_at, gm.join_time, CURRENT_TIMESTAMP(3)),
    gm.create_time = COALESCE(gm.create_time, gm.join_time, CURRENT_TIMESTAMP(3)),
    gm.update_time = COALESCE(gm.update_time, gm.create_time, CURRENT_TIMESTAMP(3));
ALTER TABLE group_member
    MODIFY COLUMN student_id BIGINT NOT NULL,
    MODIFY COLUMN assignment_id BIGINT NOT NULL COMMENT 'Denormalized from group_table.assignment_id for active-membership uniqueness',
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN join_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

DROP TEMPORARY TABLE IF EXISTS gw_duplicate_active_membership;
CREATE TEMPORARY TABLE gw_duplicate_active_membership AS
SELECT id, assignment_id, student_id, group_id,
       ROW_NUMBER() OVER (PARTITION BY assignment_id, student_id ORDER BY join_time, id) AS membership_no
FROM group_member
WHERE status = 'ACTIVE';
INSERT IGNORE INTO legacy_record_archive(source_table, source_pk, payload_json, migration_batch_no)
SELECT 'group_member_duplicate_active_assignment', CAST(id AS CHAR),
       JSON_OBJECT('groupMemberId', id, 'assignmentId', assignment_id,
                   'studentId', student_id, 'groupId', group_id,
                   'action', 'marked REMOVED; earliest active membership retained'),
       @gw_migration_batch
FROM gw_duplicate_active_membership
WHERE membership_no > 1;
UPDATE group_member gm
JOIN gw_duplicate_active_membership d ON d.id = gm.id
SET gm.status = 'REMOVED',
    gm.left_time = COALESCE(gm.left_time, CURRENT_TIMESTAMP(3)),
    gm.update_time = CURRENT_TIMESTAMP(3)
WHERE d.membership_no > 1;
DROP TEMPORARY TABLE gw_duplicate_active_membership;

UPDATE group_table g
SET current_members = (SELECT COUNT(*) FROM group_member gm WHERE gm.group_id = g.id AND gm.status = 'ACTIVE'),
    member_count = (SELECT COUNT(*) FROM group_member gm WHERE gm.group_id = g.id AND gm.status = 'ACTIVE');

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND column_name = 'attempt_no'),
    'SELECT 1',
    'ALTER TABLE submission ADD COLUMN attempt_no SMALLINT UNSIGNED NOT NULL DEFAULT 1 AFTER group_id'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND column_name = 'is_latest'),
    'SELECT 1',
    'ALTER TABLE submission ADD COLUMN is_latest TINYINT(1) NOT NULL DEFAULT 1 AFTER attempt_no'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND column_name = 'latest_guard'),
    'SELECT 1',
    'ALTER TABLE submission ADD COLUMN latest_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_latest = 1 THEN 1 ELSE NULL END) STORED AFTER is_latest'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND column_name = 'artifact_json'),
    'SELECT 1',
    'ALTER TABLE submission ADD COLUMN artifact_json JSON NULL AFTER file_path'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND column_name = 'create_time'),
    'SELECT 1',
    'ALTER TABLE submission ADD COLUMN create_time DATETIME(3) NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND column_name = 'update_time'),
    'SELECT 1',
    'ALTER TABLE submission ADD COLUMN update_time DATETIME(3) NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND column_name = 'created_at'),
    'SELECT 1',
    'ALTER TABLE submission ADD COLUMN created_at DATETIME(3) NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND column_name = 'updated_at'),
    'SELECT 1',
    'ALTER TABLE submission ADD COLUMN updated_at DATETIME(3) NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

DROP TEMPORARY TABLE IF EXISTS gw_submission_attempt;
CREATE TEMPORARY TABLE gw_submission_attempt AS
SELECT id,
       ROW_NUMBER() OVER (
           PARTITION BY assignment_id, group_id
           ORDER BY COALESCE(submit_time, '1970-01-01 00:00:00'), id
       ) AS computed_attempt,
       ROW_NUMBER() OVER (
           PARTITION BY assignment_id, group_id
           ORDER BY COALESCE(submit_time, '1970-01-01 00:00:00') DESC, id DESC
       ) AS latest_rank
FROM submission;
UPDATE submission s JOIN gw_submission_attempt a ON a.id = s.id
SET s.attempt_no = a.computed_attempt,
    s.is_latest = CASE WHEN a.latest_rank = 1 THEN 1 ELSE 0 END;
DROP TEMPORARY TABLE gw_submission_attempt;

UPDATE submission
SET submit_time = COALESCE(submit_time, graded_time, CURRENT_TIMESTAMP(3)),
    status = CASE WHEN UPPER(COALESCE(status, 'SUBMITTED')) IN
        ('DRAFT', 'SUBMITTED', 'RETURNED', 'GRADED', 'ARCHIVED')
        THEN UPPER(COALESCE(status, 'SUBMITTED')) ELSE 'SUBMITTED' END,
    teacher_score = CASE WHEN teacher_score IS NULL THEN NULL
                         ELSE LEAST(GREATEST(teacher_score, 0), 100) END,
    algorithm_score = CASE WHEN algorithm_score IS NULL THEN NULL
                           ELSE LEAST(GREATEST(algorithm_score, 0), 100) END,
    file_count = GREATEST(COALESCE(file_count, 0), 0),
    has_contribution = CASE WHEN has_contribution = 1 THEN 1 ELSE 0 END,
    group_member_count = GREATEST(COALESCE(group_member_count, 0), 0),
    is_final_score_calculated = CASE WHEN is_final_score_calculated = 1 THEN 1 ELSE 0 END,
    is_score_published = CASE WHEN is_score_published = 1 THEN 1 ELSE 0 END,
    create_time = COALESCE(create_time, submit_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, graded_time, submit_time, create_time, CURRENT_TIMESTAMP(3)),
    created_at = COALESCE(created_at, create_time, submit_time, CURRENT_TIMESTAMP(3)),
    updated_at = COALESCE(updated_at, update_time, created_at, CURRENT_TIMESTAMP(3));
ALTER TABLE submission
    MODIFY COLUMN attempt_no SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    MODIFY COLUMN is_latest TINYINT(1) NOT NULL DEFAULT 1,
    MODIFY COLUMN file_path VARCHAR(1024) NULL,
    MODIFY COLUMN file_count INT NOT NULL DEFAULT 0,
    MODIFY COLUMN submit_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    MODIFY COLUMN teacher_score DECIMAL(6,2) NULL,
    MODIFY COLUMN algorithm_score DECIMAL(6,2) NULL,
    MODIFY COLUMN has_contribution TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN group_member_count INT NOT NULL DEFAULT 0,
    MODIFY COLUMN is_final_score_calculated TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN is_score_published TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'individual_score' AND column_name = 'score_result_id'),
    'SELECT 1',
    'ALTER TABLE individual_score ADD COLUMN score_result_id BIGINT NULL AFTER student_id'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
UPDATE individual_score i LEFT JOIN teacher_users t ON t.id = i.calculated_by
SET i.calculated_by = NULL WHERE i.calculated_by IS NOT NULL AND t.id IS NULL;
UPDATE individual_score
SET calculated_at = COALESCE(calculated_at, CURRENT_TIMESTAMP(3)),
    is_published = CASE WHEN is_published = 1 THEN 1 ELSE 0 END;
ALTER TABLE individual_score
    MODIFY COLUMN teacher_score DECIMAL(6,2) NOT NULL,
    MODIFY COLUMN contribution_ratio DECIMAL(7,4) NOT NULL,
    MODIFY COLUMN individual_final_score DECIMAL(6,2) NOT NULL,
    MODIFY COLUMN difficulty_factor DECIMAL(7,4) NOT NULL DEFAULT 1.0000,
    MODIFY COLUMN collaboration_factor DECIMAL(7,4) NOT NULL DEFAULT 1.0000,
    MODIFY COLUMN calculated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN is_published TINYINT(1) NOT NULL DEFAULT 0;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'final_score' AND column_name = 'score_result_id'),
    'SELECT 1',
    'ALTER TABLE final_score ADD COLUMN score_result_id BIGINT NULL AFTER course_id'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;
INSERT IGNORE INTO legacy_record_archive(source_table, source_pk, payload_json, migration_batch_no)
SELECT 'final_score_out_of_range', CAST(id AS CHAR),
       JSON_OBJECT('id', id, 'studentId', student_id, 'assignmentId', assignment_id, 'originalScore', score),
       @gw_migration_batch
FROM final_score WHERE score < 0 OR score > 100;
UPDATE final_score f LEFT JOIN course c ON c.id = f.course_id
SET f.course_id = NULL WHERE f.course_id IS NOT NULL AND c.id IS NULL;
UPDATE final_score f LEFT JOIN teacher_users t ON t.id = f.graded_by
SET f.graded_by = NULL WHERE f.graded_by IS NOT NULL AND t.id IS NULL;
UPDATE final_score
SET type = COALESCE(NULLIF(type, ''), 'ASSIGNMENT'),
    weight = COALESCE(weight, 1),
    score = LEAST(GREATEST(score, 0), 100),
    create_time = COALESCE(create_time, graded_time, CURRENT_TIMESTAMP(3)),
    update_time = COALESCE(update_time, graded_time, create_time, CURRENT_TIMESTAMP(3));
ALTER TABLE final_score
    MODIFY COLUMN student_id BIGINT NOT NULL,
    MODIFY COLUMN type VARCHAR(30) NOT NULL DEFAULT 'ASSIGNMENT',
    MODIFY COLUMN weight DECIMAL(7,4) NOT NULL DEFAULT 1.0000,
    MODIFY COLUMN score DECIMAL(6,2) NOT NULL,
    MODIFY COLUMN graded_by BIGINT NULL,
    MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);


-- Required dictionaries and versioned paper/model metadata.
-- Groupwork minimal production seed
-- Target: MySQL 8.0.29+
-- Safe to rerun. Contains no plaintext password or deployable model artifact.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+08:00';

-- Paper-aligned rule metadata. W is stored in assignment_parts.weight, Q in
-- workload_item.share, and C = Q * W^T is computed per scoring snapshot.
INSERT INTO scoring_rule
    (rule_key, version_no, name, paper_method, formula_version,
     standard_group_size, formula_assumptions, feature_schema_json,
     normalization_json, min_score, max_score, status)
VALUES
    (
        'MCQS_PAPER_RULE',
        1,
        '多维贡献量化与差异化评分规则',
        'PAPER_MCQS',
        'paper-mcqs-v1',
        4,
        JSON_OBJECT(
            'ruleVersion', 'paper-mcqs-v1',
            'weightConstraint', 'w_i >= 0; sum(W) = 1',
            'shareConstraint', 'q_ij >= 0; sum_j(Q_ij) = 1 for every assignment part',
            'contributionEquation', 'C = Q * transpose(W); sum(C) = 1',
            'groupSizeCoefficient', JSON_OBJECT(
                'whenEqual', 'a = exp(p-r) = 1',
                'whenDifferenceInMinus1To3', 'a = 10 * (p-r)'
            ),
            'baselineEquation', 'meanScore = f + a * sin(pi*f/100)',
            'neutralContribution', '2/(p+2)',
            'paperBoundaryNormalizationForP4', JSON_OBJECT(
                'highBranch', 'cPrime = 0.54*c + 0.17',
                'lowBranch', 'cPrime = 0.67*c + 0.12'
            ),
            'actualGroupSizeRange', '1 <= r <= p+1',
            'scoreClamp', '[0,100]',
            'reviewPolicy', 'model-rule delta and anomalous inputs require teacher review'
        ),
        JSON_ARRAY(
            JSON_OBJECT('name', 'contribution', 'type', 'decimal', 'range', '[0,1]'),
            JSON_OBJECT('name', 'actualGroupSize', 'type', 'integer'),
            JSON_OBJECT('name', 'groupScore', 'type', 'decimal', 'range', '[0,100]')
        ),
        JSON_OBJECT(
            'standardGroupSizeGate', 4,
            'standardGroupSizeRole', 'applicability gate and request context; excluded from XGBoost feature vector',
            'assignmentPartWeightScale', '[0,1]',
            'workloadShareScale', '[0,1]',
            'legacyPercentConversion', 'values greater than 1 are divided by 100 during migration'
        ),
        0.00,
        100.00,
        'ACTIVE'
    )
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    paper_method = VALUES(paper_method),
    formula_version = VALUES(formula_version),
    formula_assumptions = VALUES(formula_assumptions),
    feature_schema_json = VALUES(feature_schema_json),
    normalization_json = VALUES(normalization_json),
    min_score = VALUES(min_score),
    max_score = VALUES(max_score);

-- Deterministic paper rule is runnable without a model artifact.
INSERT INTO model_version
    (model_key, version, algorithm, status, feature_schema_json,
     hyperparameters_json, metrics_json, activated_at)
VALUES
    (
        'MCQS_RULE',
        'paper-v1',
        'RULE',
        'ACTIVE',
        JSON_ARRAY('contribution', 'standardGroupSize', 'actualGroupSize', 'groupScore'),
        JSON_OBJECT('formulaVersion', 'paper-mcqs-v1'),
        JSON_OBJECT('validation', 'deterministic formula metadata only'),
        CURRENT_TIMESTAMP(3)
    )
ON DUPLICATE KEY UPDATE
    feature_schema_json = VALUES(feature_schema_json),
    hyperparameters_json = VALUES(hyperparameters_json),
    metrics_json = VALUES(metrics_json);

-- Registered verified artifact. It remains VALIDATED until an administrator
-- explicitly activates it; database uniqueness prevents two active versions.
INSERT INTO model_version
    (model_key, version, algorithm, status, artifact_uri, artifact_sha256,
     feature_schema_json, hyperparameters_json, metrics_json)
VALUES
    (
        'paper-score-p4',
        'xgb-p4-seed2025-172a3cf9486e',
        'XGBOOST',
        'VALIDATED',
        'algorithm-service://artifacts/xgboost-model.json',
        '172a3cf9486e2fa88c0d37c0401844521abdf426a6f17bf66271edb2154c162d',
        JSON_ARRAY('contribution', 'actual_group_size', 'group_score'),
        JSON_OBJECT(
            'selection', 'deterministic tuned fixed parameters',
            'parameters', JSON_OBJECT(
                'n_estimators', 800,
                'learning_rate', 0.04,
                'max_depth', 6,
                'min_child_weight', 0.5,
                'subsample', 1.0,
                'colsample_bytree', 1.0,
                'reg_alpha', 0.0,
                'reg_lambda', 1.0,
                'objective', 'reg:squarederror',
                'eval_metric', 'rmse',
                'tree_method', 'hist',
                'random_state', 2025,
                'n_jobs', 1
            ),
            'search', JSON_OBJECT(
                'available', TRUE,
                'method', 'BayesSearchCV',
                'executed', FALSE,
                'requested', FALSE,
                'note', 'Bayesian search is available via --search but was not executed for this artifact'
            ),
            'standardGroupSizeGate', 4,
            'standardGroupSizeRole', 'request context only; not a trained feature'
        ),
        JSON_OBJECT(
            'eligibility', 'ELIGIBLE',
            'holdout', JSON_OBJECT('rmse', 0.482899, 'mae', 0.356837, 'r2', 0.999700),
            'crossValidation', JSON_OBJECT('folds', 5, 'meanRmse', 0.766012,
                                           'meanMae', 0.525321, 'meanR2', 0.999259),
            'datasetSha256', '7e033aef431573642fa5b5d6cc147794647084aa2c3323056e5a39a30081d146'
        )
    )
ON DUPLICATE KEY UPDATE
    status = IF(model_version.status = 'ACTIVE', 'ACTIVE', VALUES(status)),
    artifact_uri = VALUES(artifact_uri),
    artifact_sha256 = VALUES(artifact_sha256),
    feature_schema_json = VALUES(feature_schema_json),
    hyperparameters_json = VALUES(hyperparameters_json),
    metrics_json = VALUES(metrics_json);

INSERT INTO model_version
    (model_key, version, algorithm, status, feature_schema_json, metrics_json)
VALUES
    (
        'LEGACY_IMPORT',
        'legacy-v1',
        'LEGACY',
        'RETIRED',
        JSON_ARRAY('legacyFinalScore'),
        JSON_OBJECT('purpose', 'traceability for scores imported from the pre-refactor schema')
    )
ON DUPLICATE KEY UPDATE
    feature_schema_json = VALUES(feature_schema_json),
    metrics_json = VALUES(metrics_json);



SET @gw_default_rule_id = (
    SELECT id FROM scoring_rule
    WHERE rule_key = 'MCQS_PAPER_RULE' AND version_no = 1
    LIMIT 1
);
UPDATE assignment
SET scoring_rule_id = COALESCE(scoring_rule_id, @gw_default_rule_id)
WHERE scoring_rule_id IS NULL;

SET @gw_legacy_model_id = (
    SELECT id FROM model_version
    WHERE model_key = 'LEGACY_IMPORT' AND version = 'legacy-v1'
    LIMIT 1
);

INSERT IGNORE INTO scoring_run
    (run_no, assignment_id, group_id, submission_id, scoring_rule_id, model_version_id,
     status, standard_group_size, actual_group_size, group_score, error_message,
     started_at, completed_at, create_time, update_time, created_at, updated_at)
SELECT CONCAT('LEGACY-FINAL-A-', fs.assignment_id),
       fs.assignment_id,
       NULL,
       NULL,
       @gw_default_rule_id,
       @gw_legacy_model_id,
       'PUBLISHED',
       a.standard_group_size,
       NULL,
       ROUND(AVG(fs.score), 2),
       'Imported trace only; no MCQS/XGBoost inference was claimed',
       MIN(COALESCE(fs.graded_time, fs.create_time)),
       MAX(COALESCE(fs.graded_time, fs.update_time, fs.create_time)),
       MIN(fs.create_time),
       MAX(fs.update_time),
       MIN(fs.create_time),
       MAX(fs.update_time)
FROM final_score fs
JOIN assignment a ON a.id = fs.assignment_id
WHERE fs.score_result_id IS NULL
GROUP BY fs.assignment_id, a.standard_group_size;

INSERT IGNORE INTO score_result
    (scoring_run_id, student_id, contribution, group_score, rule_score, model_score,
     model_rule_delta, anomaly_flag, manual_adjustment, final_score, status,
     explanation_json, review_reason, published_at, legacy_final_score_id,
     create_time, update_time, created_at, updated_at)
SELECT sr.id,
       fs.student_id,
       NULL,
       fs.score,
       fs.score,
       NULL,
       NULL,
       0,
       0.00,
       fs.score,
       'LEGACY_IMPORTED',
       JSON_OBJECT(
           'mode', 'legacy_import',
           'legacyFinalScoreId', fs.id,
           'assumption', 'Original score retained verbatim; no contribution, rule, or model inference was reconstructed'
       ),
       '旧系统最终成绩追溯记录；不代表 MCQS 或 XGBoost 计算结果',
       fs.graded_time,
       fs.id,
       fs.create_time,
       fs.update_time,
       fs.create_time,
       fs.update_time
FROM final_score fs
JOIN scoring_run sr ON sr.run_no = CONCAT('LEGACY-FINAL-A-', fs.assignment_id)
WHERE fs.student_id IS NOT NULL
  AND fs.score_result_id IS NULL;

UPDATE final_score fs
JOIN score_result r ON r.legacy_final_score_id = fs.id
SET fs.score_result_id = r.id
WHERE fs.score_result_id IS NULL;

UPDATE individual_score i
JOIN submission s ON s.id = i.submission_id
JOIN scoring_run sr ON sr.run_no = CONCAT('LEGACY-FINAL-A-', s.assignment_id)
JOIN score_result r ON r.scoring_run_id = sr.id AND r.student_id = i.student_id
SET i.score_result_id = r.id
WHERE i.score_result_id IS NULL;

SET foreign_key_checks = 1;


-- Add production indexes and missing legacy foreign keys only when absent.
SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'admin_users' AND index_name = 'idx_admin_status'),
    'SELECT 1',
    'ALTER TABLE admin_users ADD KEY idx_admin_status (status)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'teacher_users' AND index_name = 'idx_teacher_status'),
    'SELECT 1',
    'ALTER TABLE teacher_users ADD KEY idx_teacher_status (status)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'student_users' AND index_name = 'idx_student_status'),
    'SELECT 1',
    'ALTER TABLE student_users ADD KEY idx_student_status (status)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'major' AND index_name = 'uk_major_department_name'),
    'SELECT 1',
    'ALTER TABLE major ADD UNIQUE KEY uk_major_department_name (department_id, name)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'class' AND index_name = 'uk_class_major_name_grade'),
    'SELECT 1',
    'ALTER TABLE class ADD UNIQUE KEY uk_class_major_name_grade (major_id, name, grade)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'course' AND index_name = 'uk_course_code'),
    'SELECT 1',
    'ALTER TABLE course ADD UNIQUE KEY uk_course_code (code)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'course' AND index_name = 'idx_course_teacher'),
    'SELECT 1',
    'ALTER TABLE course ADD KEY idx_course_teacher (teacher_id)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'course' AND index_name = 'idx_course_status_term'),
    'SELECT 1',
    'ALTER TABLE course ADD KEY idx_course_status_term (status, term)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'assignment' AND index_name = 'idx_assignment_course_status'),
    'SELECT 1',
    'ALTER TABLE assignment ADD KEY idx_assignment_course_status (course_id, status)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'assignment' AND index_name = 'idx_assignment_teacher_status'),
    'SELECT 1',
    'ALTER TABLE assignment ADD KEY idx_assignment_teacher_status (teacher_id, status)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'assignment' AND index_name = 'idx_assignment_scoring_rule'),
    'SELECT 1',
    'ALTER TABLE assignment ADD KEY idx_assignment_scoring_rule (scoring_rule_id)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'group_table' AND index_name = 'uk_group_assignment_name'),
    'SELECT 1',
    'ALTER TABLE group_table ADD UNIQUE KEY uk_group_assignment_name (assignment_id, name)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'group_table' AND index_name = 'idx_group_team_status'),
    'SELECT 1',
    'ALTER TABLE group_table ADD KEY idx_group_team_status (assignment_id, team_status)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'group_member' AND index_name = 'uk_group_member'),
    'SELECT 1',
    'ALTER TABLE group_member ADD UNIQUE KEY uk_group_member (group_id, student_id)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'group_member' AND index_name = 'uk_group_member_active_assignment'),
    'SELECT 1',
    'ALTER TABLE group_member ADD UNIQUE KEY uk_group_member_active_assignment (assignment_id, student_id, active_guard)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'group_member' AND index_name = 'idx_group_member_student_status'),
    'SELECT 1',
    'ALTER TABLE group_member ADD KEY idx_group_member_student_status (student_id, status)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND index_name = 'uk_submission_latest'),
    'SELECT 1',
    'ALTER TABLE submission ADD UNIQUE KEY uk_submission_latest (assignment_id, group_id, latest_guard)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND index_name = 'idx_submission_attempt'),
    'SELECT 1',
    'ALTER TABLE submission ADD KEY idx_submission_attempt (assignment_id, group_id, attempt_no)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'submission' AND index_name = 'idx_submission_status_time'),
    'SELECT 1',
    'ALTER TABLE submission ADD KEY idx_submission_status_time (status, submit_time)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'individual_score' AND index_name = 'uk_individual_score_result'),
    'SELECT 1',
    'ALTER TABLE individual_score ADD UNIQUE KEY uk_individual_score_result (score_result_id)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'final_score' AND index_name = 'uk_final_score_result'),
    'SELECT 1',
    'ALTER TABLE final_score ADD UNIQUE KEY uk_final_score_result (score_result_id)'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'student_users'
             AND constraint_name = 'fk_student_major' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE student_users ADD CONSTRAINT fk_student_major FOREIGN KEY (major_id) REFERENCES major(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'course'
             AND constraint_name = 'fk_course_teacher' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE course ADD CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teacher_users(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'course'
             AND constraint_name = 'fk_course_major_compat' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE course ADD CONSTRAINT fk_course_major_compat FOREIGN KEY (major_id) REFERENCES major(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'assignment'
             AND constraint_name = 'fk_assignment_scoring_rule' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE assignment ADD CONSTRAINT fk_assignment_scoring_rule FOREIGN KEY (scoring_rule_id) REFERENCES scoring_rule(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'group_table'
             AND constraint_name = 'fk_group_class' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE group_table ADD CONSTRAINT fk_group_class FOREIGN KEY (class_id) REFERENCES class(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'group_table'
             AND constraint_name = 'fk_group_major' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE group_table ADD CONSTRAINT fk_group_major FOREIGN KEY (major_id) REFERENCES major(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'group_table'
             AND constraint_name = 'fk_group_teacher' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE group_table ADD CONSTRAINT fk_group_teacher FOREIGN KEY (teacher_id) REFERENCES teacher_users(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'submission'
             AND constraint_name = 'fk_submission_submitter' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE submission ADD CONSTRAINT fk_submission_submitter FOREIGN KEY (submitter_id) REFERENCES student_users(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'submission'
             AND constraint_name = 'fk_submission_grader' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE submission ADD CONSTRAINT fk_submission_grader FOREIGN KEY (graded_by) REFERENCES teacher_users(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'individual_score'
             AND constraint_name = 'fk_individual_result' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE individual_score ADD CONSTRAINT fk_individual_result FOREIGN KEY (score_result_id) REFERENCES score_result(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'individual_score'
             AND constraint_name = 'fk_individual_calculator' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE individual_score ADD CONSTRAINT fk_individual_calculator FOREIGN KEY (calculated_by) REFERENCES teacher_users(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'final_score'
             AND constraint_name = 'fk_final_student' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE final_score ADD CONSTRAINT fk_final_student FOREIGN KEY (student_id) REFERENCES student_users(id) ON UPDATE CASCADE ON DELETE RESTRICT'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'final_score'
             AND constraint_name = 'fk_final_course' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE final_score ADD CONSTRAINT fk_final_course FOREIGN KEY (course_id) REFERENCES course(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'final_score'
             AND constraint_name = 'fk_final_result' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE final_score ADD CONSTRAINT fk_final_result FOREIGN KEY (score_result_id) REFERENCES score_result(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;

SET @gw_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.table_constraints
           WHERE constraint_schema = DATABASE() AND table_name = 'final_score'
             AND constraint_name = 'fk_final_grader' AND constraint_type = 'FOREIGN KEY'),
    'SELECT 1',
    'ALTER TABLE final_score ADD CONSTRAINT fk_final_grader FOREIGN KEY (graded_by) REFERENCES teacher_users(id) ON UPDATE CASCADE ON DELETE SET NULL'
);
PREPARE gw_stmt FROM @gw_sql;
EXECUTE gw_stmt;
DEALLOCATE PREPARE gw_stmt;



-- Recreate the normalized read model only after all three legacy account tables
-- expose the required columns. Base-table ROLE_* values never leak through it.
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

SET foreign_key_checks = 1;
