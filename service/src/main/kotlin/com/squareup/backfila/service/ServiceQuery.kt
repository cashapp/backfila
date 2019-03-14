package com.squareup.backfila.service

import misk.hibernate.Constraint
import misk.hibernate.Query

interface ServiceQuery : Query<DbService> {
  @Constraint("registry_name")
  fun registryName(registryName: String): ServiceQuery
}
