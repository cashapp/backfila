CREATE TABLE `restaurants` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varbinary(128) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `orders` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `restaurant_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_restaurant_id` (`restaurant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;