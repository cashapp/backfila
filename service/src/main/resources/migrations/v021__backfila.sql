CREATE TABLE deprecation_reminders (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  registered_backfill_id bigint(20) NOT NULL,
  message_last_user boolean NOT NULL,
  repeated boolean NOT NULL,
  created_at timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY registered_backfill_id_idx (registered_backfill_id),
  KEY created_at_idx (created_at),
  CONSTRAINT fk_deprecation_reminders_backfill 
    FOREIGN KEY (registered_backfill_id) 
    REFERENCES registered_backfills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;