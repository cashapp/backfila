-- Increase pkey_cursor and related columns to maximum varbinary size
ALTER TABLE run_partitions
    MODIFY COLUMN pkey_cursor varbinary(8192) NULL DEFAULT NULL,
    MODIFY COLUMN pkey_range_start varbinary(8192) NULL DEFAULT NULL,
    MODIFY COLUMN pkey_range_end varbinary(8192) NULL DEFAULT NULL,
    MODIFY COLUMN precomputing_pkey_cursor varbinary(8192) NULL DEFAULT NULL;
