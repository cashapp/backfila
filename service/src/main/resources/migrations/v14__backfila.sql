ALTER TABLE registered_backfills
    ADD COLUMN long_term tinyint(1) NOT NULL DEFAULT 0;

