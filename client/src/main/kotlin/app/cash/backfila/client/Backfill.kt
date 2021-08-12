package app.cash.backfila.client

/**
 * Backfill base type that is cached on a per backfill run case. Must be thread-safe for calls using
 * the same backfill run id.
 */
interface Backfill
