ALTER TABLE services
  ADD COLUMN connector ENUM('HTTP', 'ENVOY') NOT NULL,
  ADD COLUMN connector_extra_data MEDIUMTEXT DEFAULT NULL,
  DROP COLUMN service_type;