CREATE TABLE services (
  id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  registry_name VARBINARY(100) NOT NULL,
  service_type ENUM('SQUARE_DC', 'CLOUD') NOT NULL,
  created_at timestamp(3) NOT NULL DEFAULT NOW(3),
  updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
  UNIQUE KEY `unq_registry_name` (registry_name)
);
