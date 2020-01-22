package app.cash.backfila.client.misk

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table
import misk.hibernate.Constraint
import misk.hibernate.DbRoot
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Query
import misk.hibernate.annotation.Keyspace

@Entity
@Keyspace("backfila_orders")
@Table(name = "restaurants")
class DbRestaurant private constructor() : DbRoot<DbRestaurant> {
  @javax.persistence.Id
  override lateinit var id: Id<DbRestaurant>

  @Column(nullable = false) lateinit var name: String

  constructor(id: Id<DbRestaurant>, name: String) : this() {
    this.id = id
    this.name = name
  }
}

interface RestaurantQuery : Query<DbRestaurant> {

  @Constraint(path = "name", operator = Operator.EQ)
  fun name(name: String): RestaurantQuery
}
