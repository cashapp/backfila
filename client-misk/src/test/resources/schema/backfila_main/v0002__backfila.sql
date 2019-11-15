CREATE TABLE `orders_seq` (
  `id` int(11) NOT NULL DEFAULT '0',
  `next_id` bigint(20) DEFAULT NULL,
  `cache` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='vitess_sequence';

INSERT INTO `orders_seq` (id, next_id, cache) VALUES (0, 1, 100);

CREATE TABLE `restaurants_seq` (
  `id` int(11) NOT NULL DEFAULT '0',
  `next_id` bigint(20) DEFAULT NULL,
  `cache` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='vitess_sequence';

INSERT INTO `restaurants_seq` (id, next_id, cache) VALUES (0, 1, 100);

CREATE TABLE `menu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varbinary(128) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;