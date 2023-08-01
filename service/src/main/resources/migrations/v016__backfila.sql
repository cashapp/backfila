ALTER TABLE backfill_runs
  ADD COLUMN target_cluster_type_override VARBINARY(200) NULL DEFAULT NULL;