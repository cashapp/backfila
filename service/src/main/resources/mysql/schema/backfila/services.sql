CREATE TABLE `services` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registry_name` varbinary(100) NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `connector` varbinary(100) NOT NULL,
  `connector_extra_data` mediumtext,
  `slack_channel` varbinary(100) DEFAULT NULL,
  `variant` varbinary(100) NOT NULL DEFAULT 'default',
  `last_registered_at` timestamp(3) NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `unq_registry_name_variant` (`registry_name`,`variant`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
