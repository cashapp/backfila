CREATE TABLE run_instances (
  id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  backfill_run_id bigint NOT NULL,
  instance_name varbinary(300) NOT NULL,
  created_at timestamp(3) NOT NULL DEFAULT NOW(3),
  updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
  version bigint NOT NULL DEFAULT 0,
  run_state ENUM('PAUSED', 'RUNNING', 'COMPLETE') NOT NULL,
  lease_token varbinary(300) NULL DEFAULT NULL,
  lease_expires_at timestamp(3) NOT NULL,

  pkey_cursor varbinary(300) NULL DEFAULT NULL,
  pkey_range_start varbinary(300) NULL DEFAULT NULL,
  pkey_range_end varbinary(300) NULL DEFAULT NULL,

  estimated_record_count bigint NULL DEFAULT NULL,
  precomputing_pkey_cursor varbinary(300) NULL DEFAULT NULL,
  computed_scanned_record_count bigint NOT NULL DEFAULT 0,
  computed_matching_record_count bigint NOT NULL DEFAULT 0,
  precomputing_done tinyint(1) NOT NULL DEFAULT 0,

  backfilled_scanned_record_count bigint NOT NULL DEFAULT 0,
  backfilled_matching_record_count bigint NOT NULL DEFAULT 0,

  UNIQUE KEY `unq_backfill_run_id_instance_name` (backfill_run_id, instance_name),
  KEY `idx_run_state_lease_expires_at` (run_state, lease_expires_at)
);
