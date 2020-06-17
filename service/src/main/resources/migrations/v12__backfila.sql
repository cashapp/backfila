ALTER TABLE run_instances
    CHANGE `instance_name` `partition_name` varbinary(300) NOT NULL,
    DROP KEY unq_backfill_run_id_instance_name,
    ADD UNIQUE KEY `unq_backfill_run_id_partition_name` (backfill_run_id, partition_name),
    RENAME TO run_partitions;