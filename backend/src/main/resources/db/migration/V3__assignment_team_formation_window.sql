ALTER TABLE assignment ADD COLUMN published_at DATETIME(3) NULL AFTER status;

UPDATE assignment
SET published_at = COALESCE(updated_at, created_at)
WHERE status IN ('PUBLISHED', 'CLOSED') AND published_at IS NULL;
