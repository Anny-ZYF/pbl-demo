-- Classes are numbered once per department. A student's major and class are
-- independent organization dimensions under the same department.

ALTER TABLE `class`
    ADD COLUMN department_id BIGINT NULL AFTER id,
    ADD KEY idx_class_department (department_id);

UPDATE `class` c
JOIN major m ON m.id = c.major_id
SET c.department_id = m.department_id
WHERE c.department_id IS NULL;

UPDATE student_users student
JOIN `class` current_class ON current_class.id = student.class_id
JOIN (
    SELECT department_id, name, MIN(id) AS canonical_id
    FROM `class`
    WHERE department_id IS NOT NULL
    GROUP BY department_id, name
) canonical ON canonical.department_id = current_class.department_id
           AND canonical.name = current_class.name
SET student.class_id = canonical.canonical_id;

UPDATE group_table team
JOIN `class` current_class ON current_class.id = team.class_id
JOIN (
    SELECT department_id, name, MIN(id) AS canonical_id
    FROM `class`
    WHERE department_id IS NOT NULL
    GROUP BY department_id, name
) canonical ON canonical.department_id = current_class.department_id
           AND canonical.name = current_class.name
SET team.class_id = canonical.canonical_id;

DELETE duplicate_class
FROM `class` duplicate_class
JOIN `class` canonical
  ON canonical.department_id = duplicate_class.department_id
 AND canonical.name = duplicate_class.name
 AND canonical.id < duplicate_class.id;

UPDATE `class`
SET major_id = NULL
WHERE department_id IS NOT NULL;

ALTER TABLE `class`
    MODIFY department_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_class_department
        FOREIGN KEY (department_id) REFERENCES department(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD UNIQUE KEY uk_class_department_name (department_id, name);
