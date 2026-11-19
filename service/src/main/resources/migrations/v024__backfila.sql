ALTER TABLE backfill_runs
    ADD KEY `idx_created_by_user_id` (created_by_user, id);
