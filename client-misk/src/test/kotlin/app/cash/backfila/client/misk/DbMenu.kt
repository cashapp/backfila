package app.cash.backfila.client.misk

import misk.hibernate.DbUnsharded
import misk.hibernate.Id
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

@Entity
@Table(name = "menu")
class DbMenu() : DbUnsharded<DbMenu> {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbMenu>

  @Column(nullable = false) lateinit var name: String

  constructor(name: String) : this() {
    this.name = name
  }
}