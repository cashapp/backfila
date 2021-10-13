package app.cash.backfila.client.jooq.config

import app.cash.backfila.client.Backfill

interface IdRecorder<K, Param> : Backfill {
  val idsRanDry: List<K>
  val idsRanWet: List<K>
}
