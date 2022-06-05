CREATE TABLE registered_parameters (
  id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  registered_backfill_id bigint NOT NULL,
  created_at timestamp(3) NOT NULL DEFAULT NOW(3),
  updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
  name varchar(300) NOT NULL,
  description varchar(1000),
  required boolean NOT NULL,

  UNIQUE KEY (registered_backfill_id, name)
);
