CREATE TABLE IF NOT EXISTS submission_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  submission_id BIGINT NOT NULL,
  storage_key VARCHAR(500) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  file_size BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_submission_attachment (submission_id, storage_key),
  CONSTRAINT fk_submission_attachment_submission FOREIGN KEY (submission_id) REFERENCES submission(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
