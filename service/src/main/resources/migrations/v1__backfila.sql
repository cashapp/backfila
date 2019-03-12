CREATE TABLE services (
  id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  registry_name VARBINARY(128) NOT NULL,
  service_type ENUM('SQUARE_DC', 'CLOUD') NOT NULL,
  UNIQUE KEY `unq_registry_name` (registry_name)
);
