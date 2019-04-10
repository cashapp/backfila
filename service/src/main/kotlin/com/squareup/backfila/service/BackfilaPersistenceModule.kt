package com.squareup.backfila.service

import misk.hibernate.HibernateEntityModule
import misk.hibernate.HibernateModule
import misk.inject.KAbstractModule

class BackfilaPersistenceModule(private val config: BackfilaConfig) : KAbstractModule() {
  override fun configure() {
    install(HibernateModule(BackfilaDb::class,
        config.data_source_clusters.values.single().writer))
    install(object : HibernateEntityModule(BackfilaDb::class) {
      override fun configureHibernate() {
        addEntities(DbService::class)
        addEntities(DbRegisteredBackfill::class)
        addEntities(DbBackfillRun::class)
        addEntities(DbRunInstance::class)
      }
    })
  }
}
