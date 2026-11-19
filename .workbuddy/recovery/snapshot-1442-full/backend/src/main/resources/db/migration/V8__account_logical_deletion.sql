-- Account deletion is a logical deletion (注销): the row stays for course, team,
-- submission and score history, but the account leaves the admin list and can no
-- longer authenticate. The login name is rewritten to a reserved tombstone so the
-- original username can be reused and role-less login stays unambiguous.

ALTER TABLE teacher_users
    ADD COLUMN deleted_at DATETIME(3) NULL,
    ADD COLUMN deleted_by_admin_id BIGINT NULL,
    ADD KEY idx_teacher_deleted (deleted_at);

ALTER TABLE student_users
    ADD COLUMN deleted_at DATETIME(3) NULL,
    ADD COLUMN deleted_by_admin_id BIGINT NULL,
    ADD KEY idx_student_deleted (deleted_at);

-- Keep deleted rows in the unified view so audit events can still resolve the
-- actor's display name; every login/refresh/admin-account query filters deleted_at.
CREATE OR REPLACE VIEW user_account_view AS
SELECT id,
       username,
       password AS password_hash,
       COALESCE(display_name, username) AS display_name,
       'ADMIN' AS role,
       status,
       must_change_password,
       token_version,
       'ADMIN' AS account_type,
       NULL AS deleted_at,
       NULL AS deleted_by_admin_id
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
       'TEACHER' AS account_type,
       deleted_at,
       deleted_by_admin_id
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
       'STUDENT' AS account_type,
       deleted_at,
       deleted_by_admin_id
FROM student_users;
