import app.cash.backfila.client.sqldelight.plugin.SqlDelightRecordSource

plugins {
  id("app.cash.backfila.client.sqldelight")
  id("app.cash.sqldelight")
  kotlin("jvm")
}

backfilaSqlDelight {
  addRecordSource(
    name = "hockeyPlayerOrigin",
    database = "app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase",
    tableName = "hockeyPlayer",
    keyName = "player_number",
    keyType = "Int",
    recordColumns = "*",
  )
}