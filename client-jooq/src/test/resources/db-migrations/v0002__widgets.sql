CREATE TABLE `widgets` (
    `widget_token` varbinary(64) NOT NULL, -- intentionally varbinary to have mixed types in the compound key
    `manufacturer_token` varchar(255) NOT NULL,
    `created_at_ms` bigint(20) NOT NULL,
    `name` varchar(128) NOT NULL,
    PRIMARY KEY (`widget_token`),
    KEY `manufacturer_created_at` (`manufacturer_token`,`created_at_ms`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;