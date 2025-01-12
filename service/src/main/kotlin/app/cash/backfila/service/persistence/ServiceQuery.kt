package app.cash.backfila.service.persistence

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Order
import misk.hibernate.Query

interface ServiceQuery : Query<DbService> {
  @Constraint("registry_name")
  fun registryName(registryName: String): ServiceQuery

  @Constraint("variant")
  fun variant(variant: String): ServiceQuery

  @Order("registry_name")
  fun orderByName(): ServiceQuery

  @Order("variant")
  fun orderByVariant(): ServiceQuery

  @Constraint("id")
  fun id(id: Id<DbService>): ServiceQuery
}
