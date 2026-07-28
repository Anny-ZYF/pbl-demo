-- Groupwork minimal anonymized demo snapshot
-- Target: MySQL 8.0.29+
-- Generated from database/groupwork_schema.sql, database/groupwork_seed.sql,
-- and synthetic demonstration rows. It contains no copied campus or production data.
--
-- Security posture:
--   * no administrator account is seeded;
--   * all teacher/student demo accounts are disabled and require password reset;
--   * the shared BCrypt value is an intentionally unknown password hash;
--   * token_version starts at 0 for account and refresh-token invalidation;
--   * the XGBoost model is VALIDATED metadata, not an automatically active model;
--   * accepted score changes are recorded append-only in score_revision.
--
-- Existing deployments should apply the Flyway migration instead of importing
-- this snapshot. Import this file only into an empty, explicitly selected database.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `admin_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL COMMENT 'BCrypt hash only',
  `display_name` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `admin_level` int NOT NULL DEFAULT '1',
  `role` varchar(20) NOT NULL DEFAULT 'ROLE_ADMIN',
  `status` tinyint(1) NOT NULL DEFAULT '1',
  `must_change_password` tinyint(1) NOT NULL DEFAULT '0',
  `token_version` int NOT NULL DEFAULT '0',
  `last_login_time` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_username` (`username`),
  KEY `idx_admin_status` (`status`),
  CONSTRAINT `chk_admin_must_change` CHECK ((`must_change_password` in (0,1))),
  CONSTRAINT `chk_admin_role` CHECK ((`role` in (_utf8mb4'ADMIN',_utf8mb4'ROLE_ADMIN'))),
  CONSTRAINT `chk_admin_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员账户';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `description` text,
  `course_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `deadline` datetime(3) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `grading_criteria` text,
  `requirements` text,
  `allowed_file_types` varchar(255) NOT NULL DEFAULT 'docx,doc,pdf',
  `max_file_size` int NOT NULL DEFAULT '10',
  `allow_online_edit` tinyint(1) NOT NULL DEFAULT '0',
  `allow_batch_submit` tinyint(1) NOT NULL DEFAULT '1',
  `require_contribution` tinyint(1) NOT NULL DEFAULT '1',
  `allow_score_view` tinyint(1) NOT NULL DEFAULT '1',
  `allow_comment_view` tinyint(1) NOT NULL DEFAULT '1',
  `allow_peer_view` tinyint(1) NOT NULL DEFAULT '0',
  `total_score` decimal(6,2) NOT NULL DEFAULT '100.00',
  `enable_intelligent_scoring` tinyint(1) NOT NULL DEFAULT '1',
  `standard_group_size` tinyint unsigned NOT NULL DEFAULT '4',
  `scoring_rule_id` bigint DEFAULT NULL,
  `team_rule_id` bigint DEFAULT NULL COMMENT 'Legacy compatibility only',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_assignment_course_status` (`course_id`,`status`),
  KEY `idx_assignment_teacher_status` (`teacher_id`,`status`),
  KEY `idx_assignment_deadline` (`deadline`),
  KEY `idx_assignment_scoring_rule` (`scoring_rule_id`),
  CONSTRAINT `fk_assignment_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_assignment_scoring_rule` FOREIGN KEY (`scoring_rule_id`) REFERENCES `scoring_rule` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_assignment_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_assignment_group_size` CHECK ((`standard_group_size` between 1 and 4)),
  CONSTRAINT `chk_assignment_score` CHECK (((`total_score` > 0) and (`total_score` <= 100))),
  CONSTRAINT `chk_assignment_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'CLOSED',_utf8mb4'ACTIVE',_utf8mb4'ENDED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=403 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队作业';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `assignment` VALUES (401,'Team Project Demo A','Synthetic anonymized workflow data only.',301,101,'2026-06-01 23:59:59.000','PUBLISHED','MCQS W/Q contribution and teacher review.','Submit one group artifact and confirm workload shares.','docx,doc,pdf',10,0,1,1,1,1,0,100.00,1,4,1,NULL,'2026-07-15 16:09:24.157','2026-07-15 16:09:24.157','2026-07-15 16:09:24.157','2026-07-15 16:09:24.157'),(402,'Team Project Demo B','Synthetic anonymized workflow data only.',302,102,'2026-06-08 23:59:59.000','PUBLISHED','MCQS W/Q contribution and teacher review.','Submit one group artifact and confirm workload shares.','docx,doc,pdf',10,0,1,1,1,1,0,100.00,1,4,1,NULL,'2026-07-15 16:09:24.157','2026-07-15 16:09:24.157','2026-07-15 16:09:24.157','2026-07-15 16:09:24.157');
DROP TABLE IF EXISTS `assignment_parts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assignment_parts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL,
  `part_name` varchar(255) NOT NULL,
  `part_description` text,
  `weight` decimal(7,6) NOT NULL COMMENT 'MCQS W_i, 0..1; sum per assignment is validated by service',
  `sort_order` int NOT NULL DEFAULT '0',
  `scoring_criteria` text,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assignment_part_name` (`assignment_id`,`part_name`),
  KEY `idx_assignment_part_order` (`assignment_id`,`sort_order`),
  CONSTRAINT `fk_assignment_part_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_assignment_part_weight` CHECK (((`weight` >= 0) and (`weight` <= 1)))
) ENGINE=InnoDB AUTO_INCREMENT=505 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCQS 评价分支 P 与教师权重 W';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `assignment_parts` VALUES (501,401,'Analysis','Synthetic analysis branch.',0.600000,1,'Evidence quality and reasoning.','2026-07-15 16:09:24.158','2026-07-15 16:09:24.158'),(502,401,'Delivery','Synthetic delivery branch.',0.400000,2,'Completeness and collaboration.','2026-07-15 16:09:24.158','2026-07-15 16:09:24.158'),(503,402,'Design','Synthetic design branch.',0.500000,1,'Design quality.','2026-07-15 16:09:24.158','2026-07-15 16:09:24.158'),(504,402,'Validation','Synthetic validation branch.',0.500000,2,'Validation quality.','2026-07-15 16:09:24.158','2026-07-15 16:09:24.158');
DROP TABLE IF EXISTS `audit_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_type` varchar(80) NOT NULL,
  `actor_role` varchar(20) DEFAULT NULL,
  `actor_id` bigint DEFAULT NULL,
  `aggregate_type` varchar(80) NOT NULL,
  `aggregate_id` varchar(80) NOT NULL,
  `request_id` varchar(80) DEFAULT NULL,
  `detail_json` json NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_aggregate` (`aggregate_type`,`aggregate_id`,`created_at`),
  KEY `idx_audit_actor` (`actor_role`,`actor_id`,`created_at`),
  KEY `idx_audit_event_time` (`event_type`,`created_at`),
  KEY `idx_audit_request` (`request_id`),
  CONSTRAINT `chk_audit_actor_role` CHECK (((`actor_role` is null) or (`actor_role` in (_utf8mb4'SYSTEM',_utf8mb4'ADMIN',_utf8mb4'TEACHER',_utf8mb4'STUDENT'))))
) ENGINE=InnoDB AUTO_INCREMENT=1904 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变业务审计事件';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `audit_event` VALUES (1901,'SCORING_RUN_PUBLISHED','TEACHER',101,'SCORING_RUN','1201','DEMO-REQ-001','{\"status\": \"PUBLISHED\", \"syntheticDemo\": true}',NULL,NULL,'2026-07-15 16:09:24.166','2026-05-23 10:00:00.000'),(1902,'SCORE_ANOMALY_DETECTED','SYSTEM',NULL,'SCORE_RESULT','1408','DEMO-REQ-002','{\"delta\": 9.27, \"syntheticDemo\": true}',NULL,NULL,'2026-07-15 16:09:24.166','2026-05-22 09:05:02.000'),(1903,'SCORE_APPEAL_ACCEPTED','TEACHER',102,'SCORE_APPEAL','1701','DEMO-REQ-003','{\"revisionId\": 1801, \"syntheticDemo\": true}',NULL,NULL,'2026-07-15 16:09:24.166','2026-05-31 09:00:00.000');
DROP TABLE IF EXISTS `class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class` (
  `id` int NOT NULL AUTO_INCREMENT,
  `major_id` bigint DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `grade` varchar(4) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `student_count` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_major_name_grade` (`major_id`,`name`,`grade`),
  KEY `idx_class_status` (`status`),
  CONSTRAINT `fk_class_major` FOREIGN KEY (`major_id`) REFERENCES `major` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_class_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班级';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `class` VALUES (1,1,'Demo Class','2024','ACTIVE',8,'2026-07-15 16:09:24.152','2026-07-15 16:09:24.152');
DROP TABLE IF EXISTS `course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `major_id` bigint DEFAULT NULL COMMENT '兼容旧库；多专业关系以 course_major 为准',
  `term` varchar(32) DEFAULT NULL,
  `grade` varchar(4) DEFAULT NULL,
  `begin_date` datetime(3) DEFAULT NULL,
  `end_date` datetime(3) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `student_count` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_code` (`code`),
  KEY `idx_course_teacher` (`teacher_id`),
  KEY `idx_course_major_compat` (`major_id`),
  KEY `idx_course_status_term` (`status`,`term`),
  CONSTRAINT `fk_course_major_compat` FOREIGN KEY (`major_id`) REFERENCES `major` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_course_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_course_dates` CHECK (((`end_date` is null) or (`begin_date` is null) or (`end_date` >= `begin_date`))),
  CONSTRAINT `chk_course_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'CLOSED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=303 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `course` VALUES (301,'DEMO-COURSE-A','Collaborative Learning Demo A',101,1,'2025-2026-2','2024','2026-03-01 00:00:00.000','2026-07-01 00:00:00.000','ACTIVE',8,'2026-07-15 16:09:24.155','2026-07-15 16:09:24.155'),(302,'DEMO-COURSE-B','Collaborative Learning Demo B',102,1,'2025-2026-2','2024','2026-03-01 00:00:00.000','2026-07-01 00:00:00.000','ACTIVE',4,'2026-07-15 16:09:24.155','2026-07-15 16:09:24.155');
DROP TABLE IF EXISTS `course_major`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_major` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `major_id` bigint NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_major` (`course_id`,`major_id`),
  KEY `idx_course_major_major` (`major_id`),
  CONSTRAINT `fk_course_major_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_course_major_major` FOREIGN KEY (`major_id`) REFERENCES `major` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程专业范围';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `course_major` VALUES (1,301,1,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(2,302,1,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156');
DROP TABLE IF EXISTS `department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `teacher_count` int NOT NULL DEFAULT '0',
  `student_count` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_name` (`name`),
  KEY `idx_department_status` (`status`),
  CONSTRAINT `chk_department_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='院系';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `department` VALUES (1,'Demo Department','ACTIVE',2,8,'2026-07-15 16:09:24.150','2026-07-15 16:09:24.150');
DROP TABLE IF EXISTS `final_score`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `final_score` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `assignment_id` bigint NOT NULL,
  `course_id` bigint DEFAULT NULL,
  `score_result_id` bigint DEFAULT NULL,
  `type` varchar(30) NOT NULL DEFAULT 'ASSIGNMENT',
  `weight` decimal(7,4) NOT NULL DEFAULT '1.0000',
  `score` decimal(6,2) NOT NULL,
  `graded_time` datetime(3) DEFAULT NULL,
  `graded_by` bigint DEFAULT NULL,
  `comment` text,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_final_student_assignment` (`student_id`,`assignment_id`),
  UNIQUE KEY `uk_final_score_result` (`score_result_id`),
  KEY `idx_final_assignment` (`assignment_id`),
  KEY `fk_final_course` (`course_id`),
  KEY `fk_final_grader` (`graded_by`),
  CONSTRAINT `fk_final_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_final_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_final_grader` FOREIGN KEY (`graded_by`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_final_result` FOREIGN KEY (`score_result_id`) REFERENCES `score_result` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_final_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_final_score` CHECK (((`score` >= 0) and (`score` <= 100)))
) ENGINE=InnoDB AUTO_INCREMENT=1609 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已发布成绩投影';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `final_score` VALUES (1601,201,401,301,1401,'ASSIGNMENT',1.0000,88.44,'2026-05-23 10:00:00.000',101,'Synthetic published projection.','2026-07-15 16:09:24.164','2026-07-15 16:09:24.164'),(1602,202,401,301,1402,'ASSIGNMENT',1.0000,78.02,'2026-05-23 10:00:00.000',101,'Synthetic published projection.','2026-07-15 16:09:24.164','2026-07-15 16:09:24.164'),(1603,203,401,301,1403,'ASSIGNMENT',1.0000,65.86,'2026-05-23 10:00:00.000',101,'Synthetic published projection.','2026-07-15 16:09:24.164','2026-07-15 16:09:24.164'),(1604,204,401,301,1404,'ASSIGNMENT',1.0000,65.86,'2026-05-23 10:00:00.000',101,'Synthetic published projection.','2026-07-15 16:09:24.164','2026-07-15 16:09:24.164'),(1605,201,402,302,1409,'ASSIGNMENT',1.0000,84.50,'2026-05-31 10:00:00.000',102,'Projection includes accepted synthetic appeal revision.','2026-07-15 16:09:24.164','2026-07-15 16:09:24.164'),(1606,202,402,302,1410,'ASSIGNMENT',1.0000,78.73,'2026-05-30 10:00:00.000',102,'Synthetic published projection.','2026-07-15 16:09:24.164','2026-07-15 16:09:24.164'),(1607,203,402,302,1411,'ASSIGNMENT',1.0000,78.73,'2026-05-30 10:00:00.000',102,'Synthetic published projection.','2026-07-15 16:09:24.164','2026-07-15 16:09:24.164'),(1608,204,402,302,1412,'ASSIGNMENT',1.0000,74.14,'2026-05-30 10:00:00.000',102,'Synthetic published projection.','2026-07-15 16:09:24.164','2026-07-15 16:09:24.164');
DROP TABLE IF EXISTS `group_branch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_branch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `name` varchar(120) NOT NULL,
  `description` text,
  `leader_student_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_branch_name` (`group_id`,`name`),
  KEY `idx_group_branch_order` (`group_id`,`sort_order`),
  KEY `fk_group_branch_leader` (`leader_student_id`),
  CONSTRAINT `fk_group_branch_group` FOREIGN KEY (`group_id`) REFERENCES `group_table` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_group_branch_leader` FOREIGN KEY (`leader_student_id`) REFERENCES `student_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_group_branch_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'COMPLETED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可选协作任务分支；不参与 MCQS W/Q 计算';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `group_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `assignment_id` bigint NOT NULL COMMENT 'Denormalized from group_table.assignment_id for active-membership uniqueness',
  `course_id` bigint DEFAULT NULL COMMENT 'Legacy compatibility; authoritative course is group_table.course_id',
  `role` varchar(20) NOT NULL DEFAULT 'MEMBER',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `active_guard` tinyint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then 1 else NULL end)) STORED,
  `join_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `left_time` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_member` (`group_id`,`student_id`),
  UNIQUE KEY `uk_group_member_active_assignment` (`assignment_id`,`student_id`,`active_guard`),
  KEY `idx_group_member_student_status` (`student_id`,`status`),
  KEY `idx_group_member_group_status` (`group_id`,`status`),
  CONSTRAINT `fk_group_member_group` FOREIGN KEY (`group_id`) REFERENCES `group_table` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_group_member_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_group_member_role` CHECK ((`role` in (_utf8mb4'LEADER',_utf8mb4'MEMBER'))),
  CONSTRAINT `chk_group_member_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'LEFT',_utf8mb4'REMOVED')))
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小组成员唯一真相源';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `group_member` (`id`, `group_id`, `student_id`, `assignment_id`, `course_id`, `role`, `status`, `join_time`, `joined_at`, `left_time`, `create_time`, `update_time`) VALUES (1,601,201,401,301,'LEADER','ACTIVE','2026-03-10 09:00:00.000','2026-03-10 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(2,601,202,401,301,'MEMBER','ACTIVE','2026-03-10 09:00:00.000','2026-03-10 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(3,601,203,401,301,'MEMBER','ACTIVE','2026-03-10 09:00:00.000','2026-03-10 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(4,601,204,401,301,'MEMBER','ACTIVE','2026-03-10 09:00:00.000','2026-03-10 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(5,602,205,401,301,'LEADER','ACTIVE','2026-03-10 09:00:00.000','2026-03-10 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(6,602,206,401,301,'MEMBER','ACTIVE','2026-03-10 09:00:00.000','2026-03-10 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(7,602,207,401,301,'MEMBER','ACTIVE','2026-03-10 09:00:00.000','2026-03-10 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(8,602,208,401,301,'MEMBER','ACTIVE','2026-03-10 09:00:00.000','2026-03-10 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(9,603,201,402,302,'LEADER','ACTIVE','2026-03-17 09:00:00.000','2026-03-17 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(10,603,202,402,302,'MEMBER','ACTIVE','2026-03-17 09:00:00.000','2026-03-17 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(11,603,203,402,302,'MEMBER','ACTIVE','2026-03-17 09:00:00.000','2026-03-17 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(12,603,204,402,302,'MEMBER','ACTIVE','2026-03-17 09:00:00.000','2026-03-17 09:00:00.000',NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159');
DROP TABLE IF EXISTS `group_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_table` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `course_id` bigint NOT NULL,
  `assignment_id` bigint NOT NULL,
  `class_id` int DEFAULT NULL,
  `major_id` bigint DEFAULT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `leader_student_id` bigint DEFAULT NULL,
  `leader_teacher_id` bigint DEFAULT NULL,
  `member_count` int NOT NULL DEFAULT '0',
  `current_members` int NOT NULL DEFAULT '0',
  `max_members` int NOT NULL DEFAULT '6',
  `status` int NOT NULL DEFAULT '1' COMMENT 'Legacy numeric lifecycle status',
  `approval_status` int NOT NULL DEFAULT '0',
  `team_status` varchar(20) NOT NULL DEFAULT 'FORMING',
  `description` text,
  `join_method` varchar(20) NOT NULL DEFAULT 'APPROVAL',
  `is_public` tinyint(1) NOT NULL DEFAULT '0',
  `invite_code` varchar(50) DEFAULT NULL,
  `tags` varchar(255) DEFAULT NULL COMMENT 'Legacy compatibility',
  `cover_image` varchar(255) DEFAULT NULL COMMENT 'Legacy compatibility',
  `activity_score` decimal(5,2) NOT NULL DEFAULT '0.00',
  `group_rating` decimal(5,2) NOT NULL DEFAULT '0.00',
  `completed_assignments` int NOT NULL DEFAULT '0',
  `average_score` decimal(6,2) NOT NULL DEFAULT '0.00',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_assignment_name` (`assignment_id`,`name`),
  UNIQUE KEY `uk_group_invite_code` (`invite_code`),
  KEY `idx_group_course` (`course_id`),
  KEY `idx_group_teacher` (`teacher_id`),
  KEY `idx_group_leader_student` (`leader_student_id`),
  KEY `idx_group_team_status` (`assignment_id`,`team_status`),
  KEY `fk_group_class` (`class_id`),
  KEY `fk_group_major` (`major_id`),
  KEY `fk_group_leader_teacher` (`leader_teacher_id`),
  CONSTRAINT `fk_group_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_group_class` FOREIGN KEY (`class_id`) REFERENCES `class` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_group_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_group_leader_student` FOREIGN KEY (`leader_student_id`) REFERENCES `student_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_group_leader_teacher` FOREIGN KEY (`leader_teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_group_major` FOREIGN KEY (`major_id`) REFERENCES `major` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_group_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_group_capacity` CHECK (((`max_members` between 1 and 50) and (`current_members` >= 0))),
  CONSTRAINT `chk_group_join_method` CHECK ((`join_method` in (_utf8mb4'FREE',_utf8mb4'APPROVAL',_utf8mb4'INVITE',_utf8mb4'TEACHER_ASSIGNED'))),
  CONSTRAINT `chk_group_team_status` CHECK ((`team_status` in (_utf8mb4'FORMING',_utf8mb4'PENDING_CONFIRMATION',_utf8mb4'CONFIRMED',_utf8mb4'COMPLETED',_utf8mb4'DISSOLVED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=604 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作业小组';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `group_table` VALUES (601,'Demo Group A1',301,401,1,1,101,201,NULL,4,4,4,1,0,'COMPLETED','Synthetic group for published scoring.','TEACHER_ASSIGNED',0,NULL,NULL,NULL,90.00,4.50,1,86.00,'2026-07-15 16:09:24.158','2026-07-15 16:09:24.158'),(602,'Demo Group A2',301,401,1,1,101,205,NULL,4,4,4,1,0,'COMPLETED','Synthetic group with a pending anomaly review.','TEACHER_ASSIGNED',0,NULL,NULL,NULL,82.00,4.10,1,78.00,'2026-07-15 16:09:24.158','2026-07-15 16:09:24.158'),(603,'Demo Group B1',302,402,1,1,102,201,NULL,4,4,4,1,0,'COMPLETED','Synthetic group with an accepted score appeal.','TEACHER_ASSIGNED',0,NULL,NULL,NULL,94.00,4.70,1,91.00,'2026-07-15 16:09:24.158','2026-07-15 16:09:24.158');
DROP TABLE IF EXISTS `import_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(80) NOT NULL,
  `import_type` varchar(40) NOT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `file_sha256` char(64) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'CREATED',
  `total_rows` int NOT NULL DEFAULT '0',
  `success_rows` int NOT NULL DEFAULT '0',
  `failure_rows` int NOT NULL DEFAULT '0',
  `error_report_json` json DEFAULT NULL,
  `created_by_admin_id` bigint DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_batch_no` (`batch_no`),
  KEY `idx_import_status` (`status`,`created_at`),
  KEY `fk_import_admin` (`created_by_admin_id`),
  CONSTRAINT `fk_import_admin` FOREIGN KEY (`created_by_admin_id`) REFERENCES `admin_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_import_counts` CHECK (((`total_rows` >= 0) and (`success_rows` >= 0) and (`failure_rows` >= 0))),
  CONSTRAINT `chk_import_hash` CHECK (((`file_sha256` is null) or regexp_like(`file_sha256`,_utf8mb4'^[0-9a-fA-F]{64}$'))),
  CONSTRAINT `chk_import_status` CHECK ((`status` in (_utf8mb4'CREATED',_utf8mb4'RUNNING',_utf8mb4'COMPLETED',_utf8mb4'PARTIAL',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批量导入审计';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `individual_score`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `individual_score` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `score_result_id` bigint DEFAULT NULL,
  `teacher_score` decimal(6,2) NOT NULL,
  `contribution_ratio` decimal(7,4) NOT NULL,
  `individual_final_score` decimal(6,2) NOT NULL,
  `difficulty_factor` decimal(7,4) NOT NULL DEFAULT '1.0000',
  `collaboration_factor` decimal(7,4) NOT NULL DEFAULT '1.0000',
  `calculated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `calculated_by` bigint DEFAULT NULL,
  `is_published` tinyint(1) NOT NULL DEFAULT '0',
  `publish_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_individual_submission_student` (`submission_id`,`student_id`),
  UNIQUE KEY `uk_individual_score_result` (`score_result_id`),
  KEY `idx_individual_student` (`student_id`),
  KEY `fk_individual_calculator` (`calculated_by`),
  CONSTRAINT `fk_individual_calculator` FOREIGN KEY (`calculated_by`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_individual_result` FOREIGN KEY (`score_result_id`) REFERENCES `score_result` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_individual_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_individual_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_individual_contribution` CHECK (((`contribution_ratio` >= 0) and (`contribution_ratio` <= 100))),
  CONSTRAINT `chk_individual_published` CHECK ((`is_published` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=1509 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='兼容个人成绩投影';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `individual_score` VALUES (1501,701,201,1401,86.00,33.0000,88.44,1.0000,1.0000,'2026-07-15 16:09:24.164',101,1,'2026-05-23 10:00:00.000'),(1502,701,202,1402,86.00,27.0000,78.02,1.0000,1.0000,'2026-07-15 16:09:24.164',101,1,'2026-05-23 10:00:00.000'),(1503,701,203,1403,86.00,20.0000,65.86,1.0000,1.0000,'2026-07-15 16:09:24.164',101,1,'2026-05-23 10:00:00.000'),(1504,701,204,1404,86.00,20.0000,65.86,1.0000,1.0000,'2026-07-15 16:09:24.164',101,1,'2026-05-23 10:00:00.000'),(1505,703,201,1409,91.00,27.5000,84.50,1.0000,1.0000,'2026-07-15 16:09:24.164',102,1,'2026-05-30 10:00:00.000'),(1506,703,202,1410,91.00,25.0000,78.73,1.0000,1.0000,'2026-07-15 16:09:24.164',102,1,'2026-05-30 10:00:00.000'),(1507,703,203,1411,91.00,25.0000,78.73,1.0000,1.0000,'2026-07-15 16:09:24.164',102,1,'2026-05-30 10:00:00.000'),(1508,703,204,1412,91.00,22.5000,74.14,1.0000,1.0000,'2026-07-15 16:09:24.164',102,1,'2026-05-30 10:00:00.000');
DROP TABLE IF EXISTS `legacy_record_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `legacy_record_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_table` varchar(64) NOT NULL,
  `source_pk` varchar(128) NOT NULL,
  `payload_json` json NOT NULL,
  `migration_batch_no` varchar(80) NOT NULL,
  `migrated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legacy_record` (`source_table`,`source_pk`),
  KEY `idx_legacy_batch` (`migration_batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仅用于无损保存无法直接映射的旧记录';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `major`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `major` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `department_id` bigint DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `student_count` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_department_name` (`department_id`,`name`),
  KEY `idx_major_status` (`status`),
  CONSTRAINT `fk_major_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_major_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `major` VALUES (1,1,'Demo Major','ACTIVE',8,'2026-07-15 16:09:24.151','2026-07-15 16:09:24.151');
DROP TABLE IF EXISTS `model_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_key` varchar(64) NOT NULL,
  `version` varchar(64) NOT NULL,
  `algorithm` varchar(30) NOT NULL DEFAULT 'XGBOOST',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `active_model_key` varchar(64) GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then `model_key` else NULL end)) STORED,
  `artifact_uri` varchar(1024) DEFAULT NULL,
  `artifact_sha256` char(64) DEFAULT NULL,
  `feature_schema_json` json NOT NULL,
  `hyperparameters_json` json DEFAULT NULL,
  `metrics_json` json DEFAULT NULL,
  `trained_at` datetime(3) DEFAULT NULL,
  `activated_at` datetime(3) DEFAULT NULL,
  `created_by_admin_id` bigint DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_key_version` (`model_key`,`version`),
  UNIQUE KEY `uk_model_one_active` (`active_model_key`),
  KEY `idx_model_status` (`model_key`,`status`),
  KEY `fk_model_admin` (`created_by_admin_id`),
  CONSTRAINT `fk_model_admin` FOREIGN KEY (`created_by_admin_id`) REFERENCES `admin_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_model_algorithm` CHECK ((`algorithm` in (_utf8mb4'XGBOOST',_utf8mb4'RULE',_utf8mb4'LEGACY'))),
  CONSTRAINT `chk_model_artifact_hash` CHECK (((`artifact_sha256` is null) or regexp_like(`artifact_sha256`,_utf8mb4'^[0-9a-fA-F]{64}$'))),
  CONSTRAINT `chk_model_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'VALIDATED',_utf8mb4'ACTIVE',_utf8mb4'RETIRED')))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评分模型版本与可复现元数据';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `model_version` (`id`, `model_key`, `version`, `algorithm`, `status`, `artifact_uri`, `artifact_sha256`, `feature_schema_json`, `hyperparameters_json`, `metrics_json`, `trained_at`, `activated_at`, `created_by_admin_id`, `create_time`, `update_time`, `created_at`, `updated_at`) VALUES (1,'MCQS_RULE','paper-v1','RULE','ACTIVE',NULL,NULL,'[\"contribution\", \"standardGroupSize\", \"actualGroupSize\", \"groupScore\"]','{\"formulaVersion\": \"paper-mcqs-v1\"}','{\"validation\": \"deterministic formula metadata only\"}',NULL,'2026-07-15 16:09:24.128',NULL,'2026-07-15 16:09:24.128','2026-07-15 16:09:24.128','2026-07-15 16:09:24.128','2026-07-15 16:09:24.128'),(2,'paper-score-p4','xgb-p4-seed2025-172a3cf9486e','XGBOOST','VALIDATED','algorithm-service://artifacts/xgboost-model.json','172a3cf9486e2fa88c0d37c0401844521abdf426a6f17bf66271edb2154c162d','[\"contribution\", \"actual_group_size\", \"group_score\"]','{\"search\": {\"note\": \"Bayesian search is available via --search but was not executed for this artifact\", \"method\": \"BayesSearchCV\", \"executed\": false, \"available\": true, \"requested\": false}, \"selection\": \"deterministic tuned fixed parameters\", \"parameters\": {\"n_jobs\": 1, \"max_depth\": 6, \"objective\": \"reg:squarederror\", \"reg_alpha\": 0.0, \"subsample\": 1.0, \"reg_lambda\": 1.0, \"eval_metric\": \"rmse\", \"tree_method\": \"hist\", \"n_estimators\": 800, \"random_state\": 2025, \"learning_rate\": 0.04, \"colsample_bytree\": 1.0, \"min_child_weight\": 0.5}, \"standardGroupSizeGate\": 4, \"standardGroupSizeRole\": \"request context only; not a trained feature\"}','{\"holdout\": {\"r2\": 0.999700, \"mae\": 0.356837, \"rmse\": 0.482899}, \"eligibility\": \"ELIGIBLE\", \"datasetSha256\": \"7e033aef431573642fa5b5d6cc147794647084aa2c3323056e5a39a30081d146\", \"crossValidation\": {\"folds\": 5, \"meanR2\": 0.999259, \"meanMae\": 0.525321, \"meanRmse\": 0.766012}}',NULL,NULL,NULL,'2026-07-15 16:09:24.135','2026-07-15 16:09:24.135','2026-07-15 16:09:24.135','2026-07-15 16:09:24.135'),(3,'LEGACY_IMPORT','legacy-v1','LEGACY','RETIRED',NULL,NULL,'[\"legacyFinalScore\"]',NULL,'{\"purpose\": \"traceability for scores imported from the pre-refactor schema\"}',NULL,NULL,NULL,'2026-07-15 16:09:24.140','2026-07-15 16:09:24.140','2026-07-15 16:09:24.140','2026-07-15 16:09:24.140');
DROP TABLE IF EXISTS `peer_evaluation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `peer_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `submission_id` bigint DEFAULT NULL,
  `reviewer_student_id` bigint DEFAULT NULL COMMENT 'NULL is allowed only for imported legacy records',
  `reviewee_student_id` bigint NOT NULL,
  `overall_score` decimal(6,2) NOT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `evaluation_type` varchar(30) NOT NULL DEFAULT 'PEER',
  `status` varchar(20) NOT NULL DEFAULT 'SUBMITTED',
  `legacy_source_id` bigint DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_peer_evaluation` (`assignment_id`,`group_id`,`reviewer_student_id`,`reviewee_student_id`,`evaluation_type`),
  UNIQUE KEY `uk_peer_evaluation_legacy` (`legacy_source_id`),
  KEY `idx_peer_reviewee` (`assignment_id`,`reviewee_student_id`),
  KEY `fk_peer_group` (`group_id`),
  KEY `fk_peer_submission` (`submission_id`),
  KEY `fk_peer_reviewer` (`reviewer_student_id`),
  KEY `fk_peer_reviewee` (`reviewee_student_id`),
  CONSTRAINT `fk_peer_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_peer_group` FOREIGN KEY (`group_id`) REFERENCES `group_table` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_peer_reviewee` FOREIGN KEY (`reviewee_student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_peer_reviewer` FOREIGN KEY (`reviewer_student_id`) REFERENCES `student_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_peer_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_peer_score` CHECK (((`overall_score` >= 0) and (`overall_score` <= 100))),
  CONSTRAINT `chk_peer_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED',_utf8mb4'INVALIDATED'))),
  CONSTRAINT `chk_peer_type` CHECK ((`evaluation_type` in (_utf8mb4'SELF',_utf8mb4'PEER',_utf8mb4'LEADER',_utf8mb4'TEACHER',_utf8mb4'LEGACY')))
) ENGINE=InnoDB AUTO_INCREMENT=1104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='补充评价证据；MCQS 核心贡献仍由 W/Q 计算';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `peer_evaluation` VALUES (1101,401,601,701,201,202,88.00,'Synthetic demo peer feedback.','PEER','SUBMITTED',NULL,'2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1102,401,602,702,205,206,80.00,'Synthetic demo peer feedback.','PEER','SUBMITTED',NULL,'2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1103,402,603,703,201,203,92.00,'Synthetic demo peer feedback.','PEER','SUBMITTED',NULL,'2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161');
DROP TABLE IF EXISTS `refresh_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `username` varchar(50) NOT NULL,
  `role` varchar(20) NOT NULL,
  `token_version` int NOT NULL DEFAULT '0',
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_used_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_user` (`role`,`user_id`,`revoked_at`),
  KEY `idx_refresh_expiry` (`expires_at`),
  CONSTRAINT `chk_refresh_hash` CHECK (regexp_like(`token_hash`,_utf8mb4'^[0-9a-fA-F]{64}$')),
  CONSTRAINT `chk_refresh_role` CHECK ((`role` in (_utf8mb4'ADMIN',_utf8mb4'TEACHER',_utf8mb4'STUDENT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仅保存哈希的刷新令牌';
/*!40101 SET character_set_client = @saved_cs_client */;

DROP TABLE IF EXISTS `score_appeal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_appeal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `score_result_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `reason` varchar(2000) NOT NULL,
  `evidence_json` json DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'SUBMITTED',
  `resolution` varchar(2000) DEFAULT NULL,
  `resolved_by_teacher_id` bigint DEFAULT NULL,
  `submitted_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `resolved_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_appeal_status` (`status`,`submitted_at`),
  KEY `idx_appeal_student` (`student_id`,`status`),
  KEY `fk_appeal_result` (`score_result_id`),
  KEY `fk_appeal_teacher` (`resolved_by_teacher_id`),
  CONSTRAINT `fk_appeal_result` FOREIGN KEY (`score_result_id`) REFERENCES `score_result` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_appeal_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_appeal_teacher` FOREIGN KEY (`resolved_by_teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_appeal_status` CHECK ((`status` in (_utf8mb4'SUBMITTED',_utf8mb4'PROCESSING',_utf8mb4'ACCEPTED',_utf8mb4'REJECTED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB AUTO_INCREMENT=1702 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成绩申诉';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `score_appeal` VALUES (1701,1409,201,'Synthetic appeal requests review of documented contribution evidence.','{\"evidence\": \"demo-only\", \"syntheticDemo\": true}','ACCEPTED','Synthetic evidence accepted; revised projection recorded append-only.',102,'2026-05-30 12:00:00.000','2026-05-31 09:00:00.000','2026-07-15 16:09:24.165','2026-07-15 16:09:24.165','2026-07-15 16:09:24.165','2026-07-15 16:09:24.165');
DROP TABLE IF EXISTS `score_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scoring_run_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `contribution` decimal(9,6) DEFAULT NULL,
  `group_score` decimal(6,2) NOT NULL,
  `rule_score` decimal(6,2) NOT NULL,
  `model_score` decimal(6,2) DEFAULT NULL,
  `model_rule_delta` decimal(6,2) DEFAULT NULL,
  `anomaly_flag` tinyint(1) NOT NULL DEFAULT '0',
  `anomaly_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `anomaly_resolution` varchar(2000) DEFAULT NULL,
  `anomaly_resolved_by_admin_id` bigint DEFAULT NULL,
  `anomaly_resolved_at` datetime(3) DEFAULT NULL,
  `manual_adjustment` decimal(6,2) NOT NULL DEFAULT '0.00',
  `final_score` decimal(6,2) NOT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'DRAFT',
  `explanation_json` json NOT NULL,
  `review_reason` varchar(2000) DEFAULT NULL,
  `reviewed_by_teacher_id` bigint DEFAULT NULL,
  `reviewed_at` datetime(3) DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  `legacy_final_score_id` bigint DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_score_result_student` (`scoring_run_id`,`student_id`),
  UNIQUE KEY `uk_score_result_legacy` (`legacy_final_score_id`),
  KEY `idx_score_result_student_status` (`student_id`,`status`),
  KEY `idx_score_result_anomaly` (`anomaly_flag`,`anomaly_status`),
  KEY `fk_score_result_reviewer` (`reviewed_by_teacher_id`),
  KEY `fk_score_result_anomaly_admin` (`anomaly_resolved_by_admin_id`),
  CONSTRAINT `fk_score_result_anomaly_admin` FOREIGN KEY (`anomaly_resolved_by_admin_id`) REFERENCES `admin_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_score_result_reviewer` FOREIGN KEY (`reviewed_by_teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_score_result_run` FOREIGN KEY (`scoring_run_id`) REFERENCES `scoring_run` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_score_result_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_score_result_anomaly` CHECK ((`anomaly_flag` in (0,1))),
  CONSTRAINT `chk_score_result_anomaly_status` CHECK ((`anomaly_status` in (_utf8mb4'PENDING',_utf8mb4'RESOLVED'))),
  CONSTRAINT `chk_score_result_contribution` CHECK (((`contribution` is null) or ((`contribution` >= 0) and (`contribution` <= 1)))),
  CONSTRAINT `chk_score_result_scores` CHECK (((`group_score` between 0 and 100) and (`rule_score` between 0 and 100) and (`final_score` between 0 and 100) and ((`model_score` is null) or (`model_score` between 0 and 100)))),
  CONSTRAINT `chk_score_result_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING_REVIEW',_utf8mb4'REVIEWED',_utf8mb4'APPROVED',_utf8mb4'PUBLISHED',_utf8mb4'LEGACY_IMPORTED')))
) ENGINE=InnoDB AUTO_INCREMENT=1413 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人差异化评分结果';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `score_result` VALUES (1401,1201,201,0.330000,86.00,88.44,88.31,0.13,0,'PENDING',NULL,NULL,NULL,0.00,88.44,'PUBLISHED','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Synthetic review completed.',101,'2026-05-22 10:00:00.000','2026-05-23 10:00:00.000',NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1402,1201,202,0.270000,86.00,78.02,78.10,0.08,0,'PENDING',NULL,NULL,NULL,0.00,78.02,'PUBLISHED','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Synthetic review completed.',101,'2026-05-22 10:00:00.000','2026-05-23 10:00:00.000',NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1403,1201,203,0.200000,86.00,65.86,65.90,0.04,0,'PENDING',NULL,NULL,NULL,0.00,65.86,'PUBLISHED','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Synthetic review completed.',101,'2026-05-22 10:00:00.000','2026-05-23 10:00:00.000',NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1404,1201,204,0.200000,86.00,65.86,65.80,0.06,0,'PENDING',NULL,NULL,NULL,0.00,65.86,'PUBLISHED','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Synthetic review completed.',101,'2026-05-22 10:00:00.000','2026-05-23 10:00:00.000',NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1405,1202,205,0.200000,78.00,59.92,60.00,0.08,0,'PENDING',NULL,NULL,NULL,0.00,59.92,'PENDING_REVIEW','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Awaiting synthetic review.',NULL,NULL,NULL,NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1406,1202,206,0.220000,78.00,63.08,63.10,0.02,0,'PENDING',NULL,NULL,NULL,0.00,63.08,'PENDING_REVIEW','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Awaiting synthetic review.',NULL,NULL,NULL,NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1407,1202,207,0.280000,78.00,72.57,72.60,0.03,0,'PENDING',NULL,NULL,NULL,0.00,72.57,'PENDING_REVIEW','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Awaiting synthetic review.',NULL,NULL,NULL,NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1408,1202,208,0.300000,78.00,75.73,85.00,9.27,1,'PENDING',NULL,NULL,NULL,0.00,75.73,'PENDING_REVIEW','{\"anomaly\": \"model-rule delta\", \"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Awaiting anomaly review.',NULL,NULL,NULL,NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1409,1203,201,0.275000,91.00,83.31,83.40,0.09,0,'PENDING',NULL,NULL,NULL,0.00,83.31,'PUBLISHED','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Synthetic review completed.',102,'2026-05-29 10:00:00.000','2026-05-30 10:00:00.000',NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1410,1203,202,0.250000,91.00,78.73,78.70,0.03,0,'PENDING',NULL,NULL,NULL,0.00,78.73,'PUBLISHED','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Synthetic review completed.',102,'2026-05-29 10:00:00.000','2026-05-30 10:00:00.000',NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1411,1203,203,0.250000,91.00,78.73,78.80,0.07,0,'PENDING',NULL,NULL,NULL,0.00,78.73,'PUBLISHED','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Synthetic review completed.',102,'2026-05-29 10:00:00.000','2026-05-30 10:00:00.000',NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1412,1203,204,0.225000,91.00,74.14,74.20,0.06,0,'PENDING',NULL,NULL,NULL,0.00,74.14,'PUBLISHED','{\"ruleVersion\": \"paper-mcqs-v1\", \"syntheticDemo\": true}','Synthetic review completed.',102,'2026-05-29 10:00:00.000','2026-05-30 10:00:00.000',NULL,'2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163');
DROP TABLE IF EXISTS `score_revision`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `score_result_id` bigint NOT NULL,
  `score_appeal_id` bigint NOT NULL,
  `revised_by_teacher_id` bigint NOT NULL,
  `previous_score` decimal(6,2) NOT NULL,
  `revised_score` decimal(6,2) NOT NULL,
  `reason` varchar(2000) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_score_revision_appeal` (`score_appeal_id`),
  KEY `idx_score_revision_result_created` (`score_result_id`,`created_at`,`id`),
  KEY `idx_score_revision_teacher_created` (`revised_by_teacher_id`,`created_at`),
  CONSTRAINT `fk_score_revision_appeal` FOREIGN KEY (`score_appeal_id`) REFERENCES `score_appeal` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_score_revision_result` FOREIGN KEY (`score_result_id`) REFERENCES `score_result` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_score_revision_teacher` FOREIGN KEY (`revised_by_teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_score_revision_changed` CHECK ((`previous_score` <> `revised_score`)),
  CONSTRAINT `chk_score_revision_reason` CHECK ((char_length(trim(`reason`)) > 0)),
  CONSTRAINT `chk_score_revision_scores` CHECK (((`previous_score` between 0 and 100) and (`revised_score` between 0 and 100)))
) ENGINE=InnoDB AUTO_INCREMENT=1802 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Append-only score revision audit';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `score_revision` VALUES (1801,1409,1701,102,83.31,84.50,'Accepted synthetic appeal; immutable original score_result retained.','2026-05-31 09:00:00.000');
DROP TABLE IF EXISTS `scoring_input_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scoring_input_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scoring_run_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `contribution` decimal(9,6) DEFAULT NULL COMMENT 'MCQS c_i = Q_i * W^T, 0..1',
  `standard_group_size` tinyint unsigned DEFAULT NULL COMMENT 'Optional projection; authoritative value is also retained in features_json',
  `actual_group_size` tinyint unsigned DEFAULT NULL COMMENT 'Optional projection; authoritative value is also retained in features_json',
  `group_score` decimal(6,2) DEFAULT NULL COMMENT 'Optional projection; authoritative value is also retained in features_json',
  `feature_schema_version` varchar(64) NOT NULL,
  `features_json` json NOT NULL,
  `source_snapshot_json` json NOT NULL,
  `feature_hash` char(64) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scoring_snapshot_student` (`scoring_run_id`,`student_id`),
  KEY `idx_scoring_snapshot_hash` (`scoring_run_id`,`feature_hash`),
  KEY `idx_scoring_snapshot_student` (`student_id`),
  CONSTRAINT `fk_scoring_snapshot_run` FOREIGN KEY (`scoring_run_id`) REFERENCES `scoring_run` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_scoring_snapshot_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_scoring_snapshot_contribution` CHECK (((`contribution` is null) or ((`contribution` >= 0) and (`contribution` <= 1)))),
  CONSTRAINT `chk_scoring_snapshot_hash` CHECK (regexp_like(`feature_hash`,_utf8mb4'^[0-9a-fA-F]{64}$'))
) ENGINE=InnoDB AUTO_INCREMENT=1313 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变评分输入快照';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `scoring_input_snapshot` VALUES (1301,1201,201,0.330000,4,4,86.00,'xgb-p4-v1','{\"groupScore\": 86, \"contribution\": 0.33, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 801}','79f23c50ed6b1e025ce28ab4760442fc7fc88978167a9a8e1dd79ad048405914','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1302,1201,202,0.270000,4,4,86.00,'xgb-p4-v1','{\"groupScore\": 86, \"contribution\": 0.27, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 801}','c94be6d2e417251fd63cc7894a02c3a7f08dc152748c90bd65003f64d6068e6a','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1303,1201,203,0.200000,4,4,86.00,'xgb-p4-v1','{\"groupScore\": 86, \"contribution\": 0.20, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 801}','aa4d2f2ee0dfa8402b196180d6f7a00abe4457fba84292c0e2e2cc10b7503947','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1304,1201,204,0.200000,4,4,86.00,'xgb-p4-v1','{\"groupScore\": 86, \"contribution\": 0.20, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 801}','c76407f37fb086d528bb3811ac73108515be6f9fe81ceeda77cc918996218551','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1305,1202,205,0.200000,4,4,78.00,'xgb-p4-v1','{\"groupScore\": 78, \"contribution\": 0.20, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 802}','6991bcd19d86b05cfdc38d90c1d98a9ba42a6c49d8d86e442b69e676b3ec3b25','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1306,1202,206,0.220000,4,4,78.00,'xgb-p4-v1','{\"groupScore\": 78, \"contribution\": 0.22, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 802}','e695ede3bb3a44d27d82fb6ce35fc49498983e4de039b79b868d928f3d8d46db','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1307,1202,207,0.280000,4,4,78.00,'xgb-p4-v1','{\"groupScore\": 78, \"contribution\": 0.28, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 802}','88f8423c9a141e93fa2bb4cc4f4bac17b37454625c783681039bcd3e0c58cce8','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1308,1202,208,0.300000,4,4,78.00,'xgb-p4-v1','{\"groupScore\": 78, \"contribution\": 0.30, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 802}','3e144b59dfab217b1b730556d12b606f7d209b16565005bac88219c325a57d2f','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1309,1203,201,0.275000,4,4,91.00,'xgb-p4-v1','{\"groupScore\": 91, \"contribution\": 0.275, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 803}','2a7f2e82add39bb5260eb663d2377603b0beb6b3ad3bc6550e842f1f80f53eea','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1310,1203,202,0.250000,4,4,91.00,'xgb-p4-v1','{\"groupScore\": 91, \"contribution\": 0.25, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 803}','b125ee64f9a6b9f99476b9657fb30eefb4cc5d142e1c64487d68c3544042c1c6','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1311,1203,203,0.250000,4,4,91.00,'xgb-p4-v1','{\"groupScore\": 91, \"contribution\": 0.25, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 803}','7975b59cb2473c0edd7e71e4a97f65897e4de5ffffc916d8302d40e4f6fe314c','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163'),(1312,1203,204,0.225000,4,4,91.00,'xgb-p4-v1','{\"groupScore\": 91, \"contribution\": 0.225, \"actualGroupSize\": 4}','{\"syntheticDemo\": true, \"workloadSetId\": 803}','a2a9bc43fc794045976055725ebb698c27e8fbbd6a79ed75ce8f52940fdd27db','2026-07-15 16:09:24.163','2026-07-15 16:09:24.163');
DROP TABLE IF EXISTS `scoring_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scoring_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_key` varchar(64) NOT NULL,
  `version_no` int NOT NULL,
  `name` varchar(120) NOT NULL,
  `paper_method` varchar(64) NOT NULL DEFAULT 'PAPER_MCQS',
  `formula_version` varchar(64) NOT NULL DEFAULT 'paper-mcqs-v1',
  `standard_group_size` tinyint unsigned NOT NULL DEFAULT '4',
  `neutral_contribution` decimal(9,6) GENERATED ALWAYS AS (cast((2.0 / (`standard_group_size` + 2)) as decimal(9,6))) STORED,
  `formula_assumptions` json NOT NULL,
  `feature_schema_json` json NOT NULL,
  `normalization_json` json DEFAULT NULL,
  `min_score` decimal(6,2) NOT NULL DEFAULT '0.00',
  `max_score` decimal(6,2) NOT NULL DEFAULT '100.00',
  `course_id` bigint DEFAULT NULL,
  `assignment_id` bigint DEFAULT NULL COMMENT 'FK is added after assignment is created',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `created_by_admin_id` bigint DEFAULT NULL,
  `created_by_teacher_id` bigint DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scoring_rule_key_version` (`rule_key`,`version_no`),
  KEY `idx_scoring_rule_scope` (`course_id`,`assignment_id`,`status`),
  KEY `fk_scoring_rule_admin` (`created_by_admin_id`),
  KEY `fk_scoring_rule_teacher` (`created_by_teacher_id`),
  KEY `fk_scoring_rule_assignment` (`assignment_id`),
  CONSTRAINT `fk_scoring_rule_admin` FOREIGN KEY (`created_by_admin_id`) REFERENCES `admin_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_scoring_rule_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_scoring_rule_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_scoring_rule_teacher` FOREIGN KEY (`created_by_teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_scoring_rule_group_size` CHECK ((`standard_group_size` between 1 and 4)),
  CONSTRAINT `chk_scoring_rule_score_range` CHECK (((`min_score` >= 0) and (`max_score` <= 100) and (`min_score` <= `max_score`))),
  CONSTRAINT `chk_scoring_rule_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'RETIRED')))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版本化评分规则与论文公式假设';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `scoring_rule` (`id`, `rule_key`, `version_no`, `name`, `paper_method`, `formula_version`, `standard_group_size`, `formula_assumptions`, `feature_schema_json`, `normalization_json`, `min_score`, `max_score`, `course_id`, `assignment_id`, `status`, `created_by_admin_id`, `created_by_teacher_id`, `create_time`, `update_time`, `created_at`, `updated_at`) VALUES (1,'MCQS_PAPER_RULE',1,'多维贡献量化与差异化评分规则','PAPER_MCQS','paper-mcqs-v1',4,'{\"scoreClamp\": \"[0,100]\", \"ruleVersion\": \"paper-mcqs-v1\", \"reviewPolicy\": \"model-rule delta and anomalous inputs require teacher review\", \"shareConstraint\": \"q_ij >= 0; sum_j(Q_ij) = 1 for every assignment part\", \"baselineEquation\": \"meanScore = f + a * sin(pi*f/100)\", \"weightConstraint\": \"w_i >= 0; sum(W) = 1\", \"neutralContribution\": \"2/(p+2)\", \"actualGroupSizeRange\": \"1 <= r <= p+1\", \"contributionEquation\": \"C = Q * transpose(W); sum(C) = 1\", \"groupSizeCoefficient\": {\"whenEqual\": \"a = exp(p-r) = 1\", \"whenDifferenceInMinus1To3\": \"a = 10 * (p-r)\"}, \"paperBoundaryNormalizationForP4\": {\"lowBranch\": \"cPrime = 0.67*c + 0.12\", \"highBranch\": \"cPrime = 0.54*c + 0.17\"}}','[{\"name\": \"contribution\", \"type\": \"decimal\", \"range\": \"[0,1]\"}, {\"name\": \"actualGroupSize\", \"type\": \"integer\"}, {\"name\": \"groupScore\", \"type\": \"decimal\", \"range\": \"[0,100]\"}]','{\"workloadShareScale\": \"[0,1]\", \"standardGroupSizeGate\": 4, \"standardGroupSizeRole\": \"applicability gate and request context; excluded from XGBoost feature vector\", \"legacyPercentConversion\": \"values greater than 1 are divided by 100 during migration\", \"assignmentPartWeightScale\": \"[0,1]\"}',0.00,100.00,NULL,NULL,'ACTIVE',NULL,NULL,'2026-07-15 16:09:24.113','2026-07-15 16:09:24.113','2026-07-15 16:09:24.113','2026-07-15 16:09:24.113');
DROP TABLE IF EXISTS `scoring_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scoring_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_no` varchar(80) NOT NULL,
  `assignment_id` bigint NOT NULL,
  `group_id` bigint DEFAULT NULL COMMENT 'NULL only for legacy assignment-level imports',
  `submission_id` bigint DEFAULT NULL COMMENT 'NULL only for legacy imports',
  `scoring_rule_id` bigint NOT NULL,
  `model_version_id` bigint DEFAULT NULL COMMENT 'NULL means deterministic rule fallback without an active XGBoost model',
  `status` varchar(30) NOT NULL DEFAULT 'CREATED',
  `standard_group_size` tinyint unsigned NOT NULL DEFAULT '4',
  `actual_group_size` tinyint unsigned DEFAULT NULL,
  `group_score` decimal(6,2) DEFAULT NULL,
  `input_hash` char(64) DEFAULT NULL,
  `error_message` varchar(2000) DEFAULT NULL,
  `triggered_by_teacher_id` bigint DEFAULT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `completed_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scoring_run_no` (`run_no`),
  KEY `idx_scoring_run_assignment_status` (`assignment_id`,`status`),
  KEY `idx_scoring_run_submission` (`submission_id`),
  KEY `idx_scoring_run_group` (`group_id`),
  KEY `fk_scoring_run_rule` (`scoring_rule_id`),
  KEY `fk_scoring_run_model` (`model_version_id`),
  KEY `fk_scoring_run_teacher` (`triggered_by_teacher_id`),
  CONSTRAINT `fk_scoring_run_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_scoring_run_group` FOREIGN KEY (`group_id`) REFERENCES `group_table` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_scoring_run_model` FOREIGN KEY (`model_version_id`) REFERENCES `model_version` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_scoring_run_rule` FOREIGN KEY (`scoring_rule_id`) REFERENCES `scoring_rule` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_scoring_run_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_scoring_run_teacher` FOREIGN KEY (`triggered_by_teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_scoring_run_hash` CHECK (((`input_hash` is null) or regexp_like(`input_hash`,_utf8mb4'^[0-9a-fA-F]{64}$'))),
  CONSTRAINT `chk_scoring_run_sizes` CHECK (((`standard_group_size` between 1 and 4) and ((`actual_group_size` is null) or (`actual_group_size` between 1 and (`standard_group_size` + 1))))),
  CONSTRAINT `chk_scoring_run_status` CHECK ((`status` in (_utf8mb4'CREATED',_utf8mb4'MODEL_COMPLETE',_utf8mb4'DEGRADED',_utf8mb4'PENDING_REVIEW',_utf8mb4'APPROVED',_utf8mb4'PUBLISHED',_utf8mb4'FAILED')))
) ENGINE=InnoDB AUTO_INCREMENT=1204 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一次可审计的评分运行';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `scoring_run` VALUES (1201,'DEMO-RUN-A1',401,601,701,1,2,'PUBLISHED',4,4,86.00,'d6f189bda59aa1218c4888134370b97487b850a1620b3bd389b1f68c7596610d',NULL,101,'2026-05-22 09:00:00.000','2026-05-22 09:00:02.000','2026-07-15 16:09:24.162','2026-07-15 16:09:24.162','2026-07-15 16:09:24.162','2026-07-15 16:09:24.162'),(1202,'DEMO-RUN-A2',401,602,702,1,2,'PENDING_REVIEW',4,4,78.00,'9275ad8d9c687775b13805c67e027d7ba785c409170abb32dc463ad2d9b79d2a',NULL,101,'2026-05-22 09:05:00.000','2026-05-22 09:05:02.000','2026-07-15 16:09:24.162','2026-07-15 16:09:24.162','2026-07-15 16:09:24.162','2026-07-15 16:09:24.162'),(1203,'DEMO-RUN-B1',402,603,703,1,2,'PUBLISHED',4,4,91.00,'a7431396b526b31a5d71db69d36ddb747c27b3dc064b82f17fb78b62519471e3',NULL,102,'2026-05-29 09:00:00.000','2026-05-29 09:00:02.000','2026-07-15 16:09:24.162','2026-07-15 16:09:24.162','2026-07-15 16:09:24.162','2026-07-15 16:09:24.162');
DROP TABLE IF EXISTS `student_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `join_method` varchar(20) NOT NULL DEFAULT 'MANUAL',
  `remark` text,
  `join_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_course` (`student_id`,`course_id`),
  KEY `idx_student_course_course_status` (`course_id`,`status`),
  CONSTRAINT `fk_student_course_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_student_course_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_student_course_method` CHECK ((`join_method` in (_utf8mb4'MANUAL',_utf8mb4'IMPORT',_utf8mb4'INVITE',_utf8mb4'AUTO'))),
  CONSTRAINT `chk_student_course_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'INACTIVE',_utf8mb4'DROPPED')))
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程名单';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `student_course` VALUES (1,201,301,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(2,202,301,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(3,203,301,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(4,204,301,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(5,205,301,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(6,206,301,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(7,207,301,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(8,208,301,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(9,201,302,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(10,202,302,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(11,203,302,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156'),(12,204,302,'ACTIVE','AUTO',NULL,'2026-07-15 16:09:24.156','2026-07-15 16:09:24.156','2026-07-15 16:09:24.156');
DROP TABLE IF EXISTS `student_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_user_id` varchar(20) NOT NULL,
  `password` varchar(100) NOT NULL COMMENT 'BCrypt hash only',
  `name` varchar(100) DEFAULT NULL,
  `major_id` bigint DEFAULT NULL,
  `class_id` int DEFAULT NULL,
  `grade` int DEFAULT NULL,
  `role` varchar(20) NOT NULL DEFAULT 'ROLE_STUDENT',
  `status` tinyint(1) NOT NULL DEFAULT '1',
  `must_change_password` tinyint(1) NOT NULL DEFAULT '0',
  `token_version` int NOT NULL DEFAULT '0',
  `last_login_time` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_user_id` (`student_user_id`),
  KEY `idx_student_major` (`major_id`),
  KEY `idx_student_class` (`class_id`),
  KEY `idx_student_status` (`status`),
  CONSTRAINT `fk_student_class` FOREIGN KEY (`class_id`) REFERENCES `class` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_student_major` FOREIGN KEY (`major_id`) REFERENCES `major` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_student_must_change` CHECK ((`must_change_password` in (0,1))),
  CONSTRAINT `chk_student_role` CHECK ((`role` in (_utf8mb4'STUDENT',_utf8mb4'ROLE_STUDENT'))),
  CONSTRAINT `chk_student_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=209 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生账户';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `student_users` VALUES (201,'S-DEMO-001','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Student 01',1,1,2024,'ROLE_STUDENT',0,1,0,NULL,'2026-07-15 16:09:24.154','2026-07-15 16:09:24.154'),(202,'S-DEMO-002','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Student 02',1,1,2024,'ROLE_STUDENT',0,1,0,NULL,'2026-07-15 16:09:24.154','2026-07-15 16:09:24.154'),(203,'S-DEMO-003','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Student 03',1,1,2024,'ROLE_STUDENT',0,1,0,NULL,'2026-07-15 16:09:24.154','2026-07-15 16:09:24.154'),(204,'S-DEMO-004','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Student 04',1,1,2024,'ROLE_STUDENT',0,1,0,NULL,'2026-07-15 16:09:24.154','2026-07-15 16:09:24.154'),(205,'S-DEMO-005','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Student 05',1,1,2024,'ROLE_STUDENT',0,1,0,NULL,'2026-07-15 16:09:24.154','2026-07-15 16:09:24.154'),(206,'S-DEMO-006','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Student 06',1,1,2024,'ROLE_STUDENT',0,1,0,NULL,'2026-07-15 16:09:24.154','2026-07-15 16:09:24.154'),(207,'S-DEMO-007','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Student 07',1,1,2024,'ROLE_STUDENT',0,1,0,NULL,'2026-07-15 16:09:24.154','2026-07-15 16:09:24.154'),(208,'S-DEMO-008','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Student 08',1,1,2024,'ROLE_STUDENT',0,1,0,NULL,'2026-07-15 16:09:24.154','2026-07-15 16:09:24.154');
DROP TABLE IF EXISTS `submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `attempt_no` smallint unsigned NOT NULL DEFAULT '1',
  `is_latest` tinyint(1) NOT NULL DEFAULT '1',
  `latest_guard` tinyint GENERATED ALWAYS AS ((case when (`is_latest` = 1) then 1 else NULL end)) STORED,
  `submitter_id` bigint DEFAULT NULL,
  `content` longtext,
  `online_content` longtext,
  `file_path` varchar(1024) DEFAULT NULL,
  `artifact_json` json DEFAULT NULL,
  `file_count` int NOT NULL DEFAULT '0',
  `submit_note` text,
  `submit_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `status` varchar(20) NOT NULL DEFAULT 'SUBMITTED',
  `teacher_score` decimal(6,2) DEFAULT NULL,
  `teacher_comment` text,
  `graded_by` bigint DEFAULT NULL,
  `graded_time` datetime(3) DEFAULT NULL,
  `algorithm_score` decimal(6,2) DEFAULT NULL COMMENT 'Legacy compatibility; score_result is authoritative',
  `has_contribution` tinyint(1) NOT NULL DEFAULT '0',
  `contribution_scores` longtext COMMENT 'Legacy JSON snapshot only',
  `group_member_count` int NOT NULL DEFAULT '0',
  `is_final_score_calculated` tinyint(1) NOT NULL DEFAULT '0',
  `is_score_published` tinyint(1) NOT NULL DEFAULT '0',
  `score_publish_time` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_latest` (`assignment_id`,`group_id`,`latest_guard`),
  KEY `idx_submission_attempt` (`assignment_id`,`group_id`,`attempt_no`),
  KEY `idx_submission_status_time` (`status`,`submit_time`),
  KEY `idx_submission_submitter` (`submitter_id`),
  KEY `fk_submission_group` (`group_id`),
  KEY `fk_submission_grader` (`graded_by`),
  CONSTRAINT `fk_submission_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_submission_grader` FOREIGN KEY (`graded_by`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_submission_group` FOREIGN KEY (`group_id`) REFERENCES `group_table` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_submission_submitter` FOREIGN KEY (`submitter_id`) REFERENCES `student_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_submission_attempt` CHECK ((`attempt_no` >= 1)),
  CONSTRAINT `chk_submission_latest` CHECK ((`is_latest` in (0,1))),
  CONSTRAINT `chk_submission_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED',_utf8mb4'RETURNED',_utf8mb4'GRADED',_utf8mb4'ARCHIVED'))),
  CONSTRAINT `chk_submission_teacher_score` CHECK (((`teacher_score` is null) or ((`teacher_score` >= 0) and (`teacher_score` <= 100))))
) ENGINE=InnoDB AUTO_INCREMENT=704 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小组作业提交';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `submission` (`id`, `assignment_id`, `group_id`, `attempt_no`, `is_latest`, `submitter_id`, `content`, `online_content`, `file_path`, `artifact_json`, `file_count`, `submit_note`, `submit_time`, `status`, `teacher_score`, `teacher_comment`, `graded_by`, `graded_time`, `algorithm_score`, `has_contribution`, `contribution_scores`, `group_member_count`, `is_final_score_calculated`, `is_score_published`, `score_publish_time`, `create_time`, `update_time`, `created_at`, `updated_at`) VALUES (701,401,601,1,1,201,'Synthetic anonymized submission A1.',NULL,NULL,'{\"kind\": \"synthetic-demo\", \"files\": []}',0,'Demo submission.','2026-05-20 10:00:00.000','GRADED',86.00,'Synthetic teacher feedback.',101,'2026-05-22 10:00:00.000',NULL,1,NULL,4,1,1,'2026-05-23 10:00:00.000','2026-07-15 16:09:24.159','2026-07-15 16:09:24.159','2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(702,401,602,1,1,205,'Synthetic anonymized submission A2.',NULL,NULL,'{\"kind\": \"synthetic-demo\", \"files\": []}',0,'Demo submission.','2026-05-20 11:00:00.000','GRADED',78.00,'Pending anomaly review.',101,'2026-05-22 11:00:00.000',NULL,1,NULL,4,1,0,NULL,'2026-07-15 16:09:24.159','2026-07-15 16:09:24.159','2026-07-15 16:09:24.159','2026-07-15 16:09:24.159'),(703,402,603,1,1,201,'Synthetic anonymized submission B1.',NULL,NULL,'{\"kind\": \"synthetic-demo\", \"files\": []}',0,'Demo submission.','2026-05-27 10:00:00.000','GRADED',91.00,'Synthetic teacher feedback.',102,'2026-05-29 10:00:00.000',NULL,1,NULL,4,1,1,'2026-05-30 10:00:00.000','2026-07-15 16:09:24.159','2026-07-15 16:09:24.159','2026-07-15 16:09:24.159','2026-07-15 16:09:24.159');
DROP TABLE IF EXISTS `teacher_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_user_id` varchar(20) NOT NULL,
  `password` varchar(100) NOT NULL COMMENT 'BCrypt hash only',
  `name` varchar(100) DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `role` varchar(20) NOT NULL DEFAULT 'ROLE_TEACHER',
  `status` tinyint(1) NOT NULL DEFAULT '1',
  `must_change_password` tinyint(1) NOT NULL DEFAULT '0',
  `token_version` int NOT NULL DEFAULT '0',
  `last_login_time` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_user_id` (`teacher_user_id`),
  KEY `idx_teacher_department` (`department_id`),
  KEY `idx_teacher_status` (`status`),
  CONSTRAINT `fk_teacher_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_teacher_must_change` CHECK ((`must_change_password` in (0,1))),
  CONSTRAINT `chk_teacher_role` CHECK ((`role` in (_utf8mb4'TEACHER',_utf8mb4'ROLE_TEACHER'))),
  CONSTRAINT `chk_teacher_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师账户';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `teacher_users` VALUES (101,'T-DEMO-001','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Teacher A',1,'ROLE_TEACHER',0,1,0,NULL,'2026-07-15 16:09:24.153','2026-07-15 16:09:24.153'),(102,'T-DEMO-002','$2a$12$CKCzQmwdHTzucQwy4wrMY./LAvAMyXRJ4lNp5qL2jM81ycVva.aDq','Demo Teacher B',1,'ROLE_TEACHER',0,1,0,NULL,'2026-07-15 16:09:24.153','2026-07-15 16:09:24.153');
DROP TABLE IF EXISTS `user_account_view`;
/*!50001 DROP VIEW IF EXISTS `user_account_view`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `user_account_view` AS SELECT 
 1 AS `id`,
 1 AS `username`,
 1 AS `password_hash`,
 1 AS `display_name`,
 1 AS `role`,
 1 AS `status`,
 1 AS `must_change_password`,
 1 AS `token_version`,
 1 AS `account_type`*/;
SET character_set_client = @saved_cs_client;
DROP TABLE IF EXISTS `workload_confirmation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workload_confirmation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workload_set_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `comment` varchar(1000) DEFAULT NULL,
  `confirmed_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workload_confirmation` (`workload_set_id`,`student_id`),
  KEY `idx_workload_confirmation_status` (`workload_set_id`,`status`),
  KEY `fk_workload_confirmation_student` (`student_id`),
  CONSTRAINT `fk_workload_confirmation_set` FOREIGN KEY (`workload_set_id`) REFERENCES `workload_set` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_workload_confirmation_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_workload_confirmation_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'CONFIRMED',_utf8mb4'OBJECTED')))
) ENGINE=InnoDB AUTO_INCREMENT=1013 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成员对 W/Q 分配版本的确认';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `workload_confirmation` VALUES (1001,801,201,'CONFIRMED','Synthetic confirmation.','2026-05-20 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1002,801,202,'CONFIRMED','Synthetic confirmation.','2026-05-20 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1003,801,203,'CONFIRMED','Synthetic confirmation.','2026-05-20 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1004,801,204,'CONFIRMED','Synthetic confirmation.','2026-05-20 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1005,802,205,'CONFIRMED','Synthetic confirmation.','2026-05-20 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1006,802,206,'CONFIRMED','Synthetic confirmation.','2026-05-20 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1007,802,207,'CONFIRMED','Synthetic confirmation.','2026-05-20 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1008,802,208,'CONFIRMED','Synthetic confirmation.','2026-05-20 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1009,803,201,'CONFIRMED','Synthetic confirmation.','2026-05-27 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1010,803,202,'CONFIRMED','Synthetic confirmation.','2026-05-27 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1011,803,203,'CONFIRMED','Synthetic confirmation.','2026-05-27 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161'),(1012,803,204,'CONFIRMED','Synthetic confirmation.','2026-05-27 08:00:00.000','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161','2026-07-15 16:09:24.161');
DROP TABLE IF EXISTS `workload_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workload_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workload_set_id` bigint NOT NULL,
  `assignment_part_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `branch_id` bigint DEFAULT NULL COMMENT 'Optional collaboration branch; excluded from MCQS dimensions',
  `share` decimal(7,6) NOT NULL COMMENT 'MCQS Q_ij, 0..1; sum per set and part is validated by service',
  `task_title` varchar(200) DEFAULT NULL,
  `task_description` text,
  `planned_hours` decimal(8,2) DEFAULT NULL,
  `actual_hours` decimal(8,2) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PLANNED',
  `evidence_json` text COMMENT 'Human evidence text; name retained for API compatibility',
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workload_item_q` (`workload_set_id`,`assignment_part_id`,`student_id`),
  KEY `idx_workload_item_student` (`student_id`,`status`),
  KEY `idx_workload_item_part` (`assignment_part_id`),
  KEY `idx_workload_item_branch` (`branch_id`),
  CONSTRAINT `fk_workload_item_branch` FOREIGN KEY (`branch_id`) REFERENCES `group_branch` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_workload_item_part` FOREIGN KEY (`assignment_part_id`) REFERENCES `assignment_parts` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_workload_item_set` FOREIGN KEY (`workload_set_id`) REFERENCES `workload_set` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_workload_item_student` FOREIGN KEY (`student_id`) REFERENCES `student_users` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_workload_item_hours` CHECK ((((`planned_hours` is null) or (`planned_hours` >= 0)) and ((`actual_hours` is null) or (`actual_hours` >= 0)))),
  CONSTRAINT `chk_workload_item_share` CHECK (((`share` >= 0) and (`share` <= 1))),
  CONSTRAINT `chk_workload_item_status` CHECK ((`status` in (_utf8mb4'PLANNED',_utf8mb4'IN_PROGRESS',_utf8mb4'COMPLETED',_utf8mb4'VERIFIED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB AUTO_INCREMENT=925 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCQS 每个评价分支与成员的 Q 份额';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `workload_item` VALUES (901,801,501,201,NULL,0.350000,'Demo task A1-1',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',1,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(902,801,501,202,NULL,0.250000,'Demo task A1-2',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',2,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(903,801,501,203,NULL,0.200000,'Demo task A1-3',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',3,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(904,801,501,204,NULL,0.200000,'Demo task A1-4',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',4,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(905,801,502,201,NULL,0.300000,'Demo task A1-5',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',1,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(906,801,502,202,NULL,0.300000,'Demo task A1-6',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',2,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(907,801,502,203,NULL,0.200000,'Demo task A1-7',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',3,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(908,801,502,204,NULL,0.200000,'Demo task A1-8',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',4,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(909,802,501,205,NULL,0.200000,'Demo task A2-1',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',1,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(910,802,501,206,NULL,0.200000,'Demo task A2-2',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',2,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(911,802,501,207,NULL,0.300000,'Demo task A2-3',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',3,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(912,802,501,208,NULL,0.300000,'Demo task A2-4',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',4,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(913,802,502,205,NULL,0.200000,'Demo task A2-5',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',1,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(914,802,502,206,NULL,0.250000,'Demo task A2-6',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',2,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(915,802,502,207,NULL,0.250000,'Demo task A2-7',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',3,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(916,802,502,208,NULL,0.300000,'Demo task A2-8',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',4,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(917,803,503,201,NULL,0.250000,'Demo task B1-1',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',1,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(918,803,503,202,NULL,0.250000,'Demo task B1-2',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',2,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(919,803,503,203,NULL,0.250000,'Demo task B1-3',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',3,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(920,803,503,204,NULL,0.250000,'Demo task B1-4',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',4,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(921,803,504,201,NULL,0.300000,'Demo task B1-5',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',1,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(922,803,504,202,NULL,0.250000,'Demo task B1-6',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',2,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(923,803,504,203,NULL,0.250000,'Demo task B1-7',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',3,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(924,803,504,204,NULL,0.200000,'Demo task B1-8',NULL,NULL,NULL,'VERIFIED','{\"source\":\"synthetic-demo\"}',4,'2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160');
DROP TABLE IF EXISTS `workload_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workload_set` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `assignment_id` bigint NOT NULL,
  `version_no` int NOT NULL DEFAULT '1',
  `status` varchar(30) NOT NULL DEFAULT 'DRAFT',
  `created_by_teacher_id` bigint DEFAULT NULL,
  `created_by_student_id` bigint DEFAULT NULL,
  `submitted_at` datetime(3) DEFAULT NULL,
  `locked_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workload_set_version` (`group_id`,`assignment_id`,`version_no`),
  KEY `idx_workload_set_status` (`assignment_id`,`status`),
  KEY `fk_workload_set_teacher` (`created_by_teacher_id`),
  KEY `fk_workload_set_student` (`created_by_student_id`),
  CONSTRAINT `fk_workload_set_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_workload_set_group` FOREIGN KEY (`group_id`) REFERENCES `group_table` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_workload_set_student` FOREIGN KEY (`created_by_student_id`) REFERENCES `student_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_workload_set_teacher` FOREIGN KEY (`created_by_teacher_id`) REFERENCES `teacher_users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_workload_set_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING_CONFIRMATION',_utf8mb4'CONFIRMED',_utf8mb4'LOCKED',_utf8mb4'SUPERSEDED'))),
  CONSTRAINT `chk_workload_set_version` CHECK ((`version_no` >= 1))
) ENGINE=InnoDB AUTO_INCREMENT=804 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一次小组 W/Q 工作量分配版本';
/*!40101 SET character_set_client = @saved_cs_client */;

INSERT INTO `workload_set` VALUES (801,601,401,1,'LOCKED',NULL,201,'2026-05-19 10:00:00.000','2026-05-20 09:00:00.000','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(802,602,401,1,'LOCKED',NULL,205,'2026-05-19 11:00:00.000','2026-05-20 09:00:00.000','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160'),(803,603,402,1,'LOCKED',NULL,201,'2026-05-26 10:00:00.000','2026-05-27 09:00:00.000','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160','2026-07-15 16:09:24.160');
/*!50001 DROP VIEW IF EXISTS `user_account_view`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 SQL SECURITY INVOKER */
/*!50001 VIEW `user_account_view` AS select `admin_users`.`id` AS `id`,`admin_users`.`username` AS `username`,`admin_users`.`password` AS `password_hash`,coalesce(`admin_users`.`display_name`,`admin_users`.`username`) AS `display_name`,'ADMIN' AS `role`,`admin_users`.`status` AS `status`,`admin_users`.`must_change_password` AS `must_change_password`,`admin_users`.`token_version` AS `token_version`,'ADMIN' AS `account_type` from `admin_users` union all select `teacher_users`.`id` AS `id`,`teacher_users`.`teacher_user_id` AS `username`,`teacher_users`.`password` AS `password_hash`,coalesce(`teacher_users`.`name`,`teacher_users`.`teacher_user_id`) AS `display_name`,'TEACHER' AS `role`,`teacher_users`.`status` AS `status`,`teacher_users`.`must_change_password` AS `must_change_password`,`teacher_users`.`token_version` AS `token_version`,'TEACHER' AS `account_type` from `teacher_users` union all select `student_users`.`id` AS `id`,`student_users`.`student_user_id` AS `username`,`student_users`.`password` AS `password_hash`,coalesce(`student_users`.`name`,`student_users`.`student_user_id`) AS `display_name`,'STUDENT' AS `role`,`student_users`.`status` AS `status`,`student_users`.`must_change_password` AS `must_change_password`,`student_users`.`token_version` AS `token_version`,'STUDENT' AS `account_type` from `student_users` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
