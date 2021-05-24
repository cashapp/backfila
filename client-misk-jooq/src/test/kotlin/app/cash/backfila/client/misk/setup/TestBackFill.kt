package app.cash.backfila.client.misk.setup

interface TestBackFill<K, Param> {
  val idsRanDry: List<K>
  val idsRanWet: List<K>
}
