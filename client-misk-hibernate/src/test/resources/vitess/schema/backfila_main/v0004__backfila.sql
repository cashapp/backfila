CREATE TABLE `restaurants_seq` (
    `id` tinyint unsigned NOT NULL DEFAULT '0',
    `next_id` bigint unsigned DEFAULT NULL,
    `cache` bigint unsigned DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='vitess_sequence';