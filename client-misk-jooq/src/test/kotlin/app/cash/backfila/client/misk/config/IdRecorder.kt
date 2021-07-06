package app.cash.backfila.client.misk.config

import app.cash.backfila.client.Backfill

interface IdRecorder<K, Param> : Backfill {
  val idsRanDry: List<K>
  val idsRanWet: List<K>
}
