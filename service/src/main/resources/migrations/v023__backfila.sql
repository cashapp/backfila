ALTER TABLE event_logs
    MODIFY COLUMN message varbinary(8192) NOT NULL;
