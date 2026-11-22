CREATE TABLE `registered_parameters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registered_backfill_id` bigint NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `name` varchar(300) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `required` tinyint(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `registered_backfill_id` (`registered_backfill_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
