-- Dev-only seed for the partitions table UI (testing filter + sort).
--
-- Replaces an existing backfill_run's partitions with 10 synthetic rows in
-- mixed states (RUNNING/PAUSED/COMPLETE/CANCELLED) and varied progress.
--
-- Usage:
--   1. In the Backfila UI, create + start any backfill (e.g. BurgerFlippingBackfill).
--   2. Find its backfill_run_id (visible in the backfill detail page URL, or:
--        mysql -u root backfila_development -e \
--          "SELECT id, state FROM backfill_runs ORDER BY id DESC LIMIT 5;"
--   3. Run this script, passing the id via --init-command:
--        mysql -u root backfila_development \
--          --init-command="SET @run_id := <BACKFILL_RUN_ID>" \
--          < dev/seed_partitions.sql
--   4. Reload the backfill detail page.
--
-- Notes:
--   - lease_expires_at is set near the MySQL TIMESTAMP cap (2038-01-19) to keep
--     the real runner from leasing these rows. Values past 2038 silently get
--     stored as zero — which Hibernate then rejects as "Zero date value prohibited".
--   - If the parent backfill is RUNNING, the runner will skip these synthetic
--     partitions (lease guard). If you want a truly static view, pause it.

SELECT CONCAT('Seeding 10 synthetic partitions for backfill_run_id = ', @run_id) AS status;

DELETE FROM run_partitions WHERE backfill_run_id = @run_id;

INSERT INTO run_partitions
  (backfill_run_id, partition_name, run_state, lease_expires_at,
   pkey_range_start, pkey_range_end, pkey_cursor,
   computed_scanned_record_count, computed_matching_record_count, precomputing_done,
   backfilled_scanned_record_count, backfilled_matching_record_count,
   scanned_records_per_minute, matching_records_per_minute)
VALUES
  (@run_id, 'shard-01-running-fast',      'RUNNING',   '2037-12-31 23:59:59', '0',  '5000',  '4500',  5000,  5000, 1,  4500,  4500, 1500, 1500),
  (@run_id, 'shard-02-running-medium',    'RUNNING',   '2037-12-31 23:59:59', '0', '20000', '10000', 20000, 20000, 1, 10000, 10000,  800,  800),
  (@run_id, 'shard-03-running-slow',      'RUNNING',   '2037-12-31 23:59:59', '0', '50000',  '5000', 50000, 50000, 1,  5000,  5000,  200,  200),
  (@run_id, 'shard-04-running-stalled',   'RUNNING',   '2037-12-31 23:59:59', '0', '10000',  '3000', 10000, 10000, 1,  3000,  3000,    0,    0),
  (@run_id, 'shard-05-running-computing', 'RUNNING',   '2037-12-31 23:59:59', '0', '10000',     '0',  3000,  3000, 0,     0,     0,  600,  600),
  (@run_id, 'shard-06-paused-30pct',      'PAUSED',    '2037-12-31 23:59:59', '0', '10000',  '3000', 10000, 10000, 1,  3000,  3000, NULL, NULL),
  (@run_id, 'shard-07-paused-near-done',  'PAUSED',    '2037-12-31 23:59:59', '0', '10000',  '9700', 10000, 10000, 1,  9700,  9700, NULL, NULL),
  (@run_id, 'shard-08-complete-small',    'COMPLETE',  '2037-12-31 23:59:59', '0',  '2000',  '2000',  2000,  2000, 1,  2000,  2000, NULL, NULL),
  (@run_id, 'shard-09-complete-big',      'COMPLETE',  '2037-12-31 23:59:59', '0', '40000', '40000', 40000, 40000, 1, 40000, 40000, NULL, NULL),
  (@run_id, 'shard-10-cancelled-25pct',   'CANCELLED', '2037-12-31 23:59:59', '0', '10000',  '2500', 10000, 10000, 1,  2500,  2500, NULL, NULL);

SELECT run_state, COUNT(*) AS n
FROM run_partitions WHERE backfill_run_id = @run_id GROUP BY run_state;
