ALTER TABLE backfill_runs
    ADD COLUMN extra_sleep_ms BIGINT NOT NULL DEFAULT 0;
