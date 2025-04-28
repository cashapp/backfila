-- Add soft_deleted column to backfill_runs
ALTER TABLE backfill_runs
    ADD COLUMN soft_deleted BOOLEAN NOT NULL DEFAULT FALSE;