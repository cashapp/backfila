CREATE TABLE backfill_runs (
  id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  service_id bigint NOT NULL,
  registered_backfill_id bigint NOT NULL,
  pipeline_target_backfill_id  bigint NULL DEFAULT NULL,
  created_at timestamp(3) NOT NULL DEFAULT NOW(3),
  updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
  version bigint NOT NULL DEFAULT 0,
  state ENUM('PAUSED', 'RUNNING', 'COMPLETE') NOT NULL,
  parameter_map mediumtext NULL DEFAULT NULL,
  created_by_user varbinary(300) NULL DEFAULT NULL,
  approved_by_user varbinary(300) NULL DEFAULT NULL,
  approved_at timestamp(3) NULL DEFAULT NULL,
  scan_size bigint NOT NULL,
  batch_size bigint NOT NULL,
  num_threads bigint NOT NULL,
  KEY `idx_service_id` (service_id)
);