ALTER TABLE backfill_runs
  ADD COLUMN dry_run TINYINT(1) NOT NULL DEFAULT 0 AFTER parameter_map;