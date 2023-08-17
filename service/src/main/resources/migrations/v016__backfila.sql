ALTER TABLE services
  ADD COLUMN variant VARBINARY(100) NULL DEFAULT NULL,
  DROP KEY `unq_registry_name`,
  ADD UNIQUE KEY `unq_registry_name_variant` (registry_name, variant);