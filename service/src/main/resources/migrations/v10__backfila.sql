ALTER TABLE run_instances
    ADD COLUMN scanned_records_per_minute BIGINT NULL,
    ADD COLUMN matching_records_per_minute BIGINT NULL;
