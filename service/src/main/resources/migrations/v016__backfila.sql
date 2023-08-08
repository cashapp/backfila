ALTER TABLE services
  ADD COLUMN flavor VARBINARY(100) NULL DEFAULT NULL,
  DROP KEY `unq_registry_name`,
  ADD UNIQUE KEY `unq_registry_name_flavor` (registry_name, flavor);