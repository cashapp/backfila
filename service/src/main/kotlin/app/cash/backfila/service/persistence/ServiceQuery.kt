package app.cash.backfila.service.persistence

import misk.hibernate.Constraint
import misk.hibernate.Operator
import misk.hibernate.Order
import misk.hibernate.Query

interface ServiceQuery : Query<DbService> {
  @Constraint("registry_name")
  fun registryName(registryName: String): ServiceQuery

  @Constraint("flavor", operator = Operator.EQ_OR_IS_NULL)
  fun flavor(flavor: String?): ServiceQuery

  @Order("registry_name")
  fun orderByName(): ServiceQuery

  @Order("flavor")
  fun orderByFlavor(): ServiceQuery
}
