package app.cash.backfila.client.misk

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import misk.hibernate.Constraint
import misk.hibernate.DbUnsharded
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Query

@Entity
@Table(name = "menu")
class DbMenu() : DbUnsharded<DbMenu> {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbMenu>

  @Column(nullable = false)
  lateinit var name: String

  constructor(name: String) : this() {
    this.name = name
  }
}

interface MenuQuery : Query<DbMenu> {
  @Constraint(path = "name", operator = Operator.EQ)
  fun name(name: String): MenuQuery

  @Constraint(path = "id", operator = Operator.EQ)
  fun id(id: Id<DbMenu>): MenuQuery
}
