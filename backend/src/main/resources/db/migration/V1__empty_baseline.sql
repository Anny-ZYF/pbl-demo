-- Flyway baseline marker for Groupwork.
--
-- New databases apply V1 and then V2, which creates the complete core schema.
-- Existing pre-Flyway databases must use baseline-on-migrate with baselineVersion=1;
-- Flyway then applies V2 exactly once without replaying legacy application DDL.
-- This migration is intentionally empty.

SELECT 1;
