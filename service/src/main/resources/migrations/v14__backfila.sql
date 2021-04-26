ALTER TABLE run_partitions
    CHANGE `run_state` `partition_state` enum('PAUSED','RUNNING','COMPLETE','STALE','CANCELLED') NOT NULL,
    DROP KEY idx_run_state_lease_expires_at,
    ADD KEY `idx_partition_state_lease_expires_at` (`partition_state`,`lease_expires_at`);