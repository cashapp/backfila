CREATE TABLE `registered_backfills` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `service_id` bigint NOT NULL,
  `name` varbinary(300) NOT NULL,
  `active` tinyint(1) DEFAULT NULL,
  `deleted_in_service_at` timestamp(3) NULL DEFAULT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `type_provided` varbinary(300) DEFAULT NULL,
  `type_consumed` varbinary(300) DEFAULT NULL,
  `parameter_names` mediumtext,
  `requires_approval` tinyint(1) NOT NULL DEFAULT '0',
  `delete_by` datetime DEFAULT NULL,
  `unit` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unq_service_id_active_name` (`service_id`,`active`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
