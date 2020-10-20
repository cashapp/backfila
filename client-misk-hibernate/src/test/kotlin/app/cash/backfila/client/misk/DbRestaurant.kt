package app.cash.backfila.client.misk

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import misk.hibernate.DbRoot
import misk.hibernate.Id
import misk.hibernate.annotation.Keyspace

@Entity
@Keyspace("backfila_orders")
@Table(name = "restaurants")
class DbRestaurant private constructor() : DbRoot<DbRestaurant> {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbRestaurant>

  @Column(nullable = false) lateinit var name: String

  constructor(name: String) : this() {
    this.name = name
  }

  interface Query : misk.hibernate.Query<DbRestaurant>
}
