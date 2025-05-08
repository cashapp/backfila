ALTER TABLE backfill_runs
    ADD COLUMN `deleted_at` timestamp(3) NULL DEFAULT NULL;