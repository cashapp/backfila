CREATE TABLE registered_backfills (
  id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  service_id bigint NOT NULL,
  name varbinary(300) NOT NULL,
  deleted_in_service_at timestamp(3) NULL DEFAULT NULL,
  created_at timestamp(3) NOT NULL DEFAULT NOW(3),
  updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
  type_provided varbinary(300) NULL DEFAULT NULL,
  type_consumed varbinary(300) NULL DEFAULT NULL,
  parameter_names blob NULL DEFAULT NULL,
  UNIQUE KEY `unq_service_id_name_deleted_in_service` (service_id, name, deleted_in_service_at)
);
