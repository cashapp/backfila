CREATE TABLE `event_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `backfill_run_id` bigint NOT NULL,
  `partition_id` bigint DEFAULT NULL,
  `user` varbinary(300) DEFAULT NULL,
  `type` enum('STATE_CHANGE','CONFIG_CHANGE','ERROR') NOT NULL,
  `message` varbinary(8192) NOT NULL,
  `extra_data` mediumtext,
  PRIMARY KEY (`id`),
  KEY `idx_backfill_run_id` (`backfill_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
