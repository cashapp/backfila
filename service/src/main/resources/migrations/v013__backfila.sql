CREATE TABLE event_logs (
  id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  created_at timestamp(3) NOT NULL DEFAULT NOW(3),
  updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
  backfill_run_id bigint NOT NULL,
  partition_id bigint NULL DEFAULT NULL,
  user varbinary(300) NULL DEFAULT NULL,

  type ENUM('STATE_CHANGE', 'CONFIG_CHANGE', 'ERROR') NOT NULL,

  message varbinary(500) NOT NULL,

  extra_data MEDIUMTEXT NULL DEFAULT NULL,

  KEY `idx_backfill_run_id` (backfill_run_id)
)
