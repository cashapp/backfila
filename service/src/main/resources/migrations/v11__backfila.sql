ALTER TABLE registered_backfills
    DROP KEY `unq_service_id_name_active`,
    /* We query on service+active, or on service+active+name */
    ADD UNIQUE KEY `unq_service_id_active_name` (service_id, active, name);
