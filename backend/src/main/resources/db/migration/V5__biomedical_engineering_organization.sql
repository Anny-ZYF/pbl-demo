-- Add the Biomedical Engineering organization used by account creation and CSV import.

INSERT INTO department(name, status)
VALUES ('生物医学工程学院', 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT INTO major(department_id, name, status)
SELECT d.id, major_name.name, 'ACTIVE'
FROM department d
JOIN (
    SELECT '智能科学与技术' AS name
    UNION ALL SELECT '数据科学与大数据'
    UNION ALL SELECT '信息管理与信息系统'
    UNION ALL SELECT '智能医学工程'
    UNION ALL SELECT '生物医学工程'
) major_name
WHERE d.name = '生物医学工程学院'
  AND NOT EXISTS (
      SELECT 1
      FROM major existing_major
      WHERE existing_major.department_id = d.id
        AND existing_major.name = major_name.name
  );

UPDATE major m
JOIN department d ON d.id = m.department_id
SET m.status = 'ACTIVE'
WHERE d.name = '生物医学工程学院'
  AND m.name IN ('智能科学与技术', '数据科学与大数据', '信息管理与信息系统', '智能医学工程', '生物医学工程');

INSERT INTO `class`(major_id, name, grade, status)
SELECT m.id, CAST(class_number.number AS CHAR), NULL, 'ACTIVE'
FROM major m
JOIN department d ON d.id = m.department_id
CROSS JOIN (
    SELECT 1 AS number UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
    UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
    UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
) class_number
WHERE d.name = '生物医学工程学院'
  AND m.name IN ('智能科学与技术', '数据科学与大数据', '信息管理与信息系统', '智能医学工程', '生物医学工程')
  AND NOT EXISTS (
      SELECT 1
      FROM `class` existing_class
      WHERE existing_class.major_id = m.id
        AND existing_class.name = CAST(class_number.number AS CHAR)
  );

UPDATE `class` c
JOIN major m ON m.id = c.major_id
JOIN department d ON d.id = m.department_id
SET c.status = 'ACTIVE'
WHERE d.name = '生物医学工程学院'
  AND m.name IN ('智能科学与技术', '数据科学与大数据', '信息管理与信息系统', '智能医学工程', '生物医学工程')
  AND CAST(c.name AS UNSIGNED) BETWEEN 1 AND 30
  AND c.name = CAST(CAST(c.name AS UNSIGNED) AS CHAR);
