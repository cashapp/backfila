package app.cash.backfila.client.misk

import misk.hibernate.DbEntity
import misk.hibernate.Id
import javax.inject.Inject

class PkeySqlAdapter @Inject constructor() {
  @Suppress("UNCHECKED_CAST") // We use runtime checks to guarantee casts are safe.
  fun <Pkey> pkeyFromString(type: Class<Pkey>, sqlString: String): Pkey {
    return when (type) {
      String::class.java -> sqlString as Pkey
      Id::class.java -> Id<DbPlaceholder>(sqlString.toLong()) as Pkey
      else -> throw IllegalArgumentException(
          "Unsupported backfill primary key type: $type. Add an adapter to this class or "
              + "use a different type as your pkey.")
    }
  }

  /** This placeholder exists so we can create an Id<*>() without a type parameter. */
  private class DbPlaceholder : DbEntity<DbPlaceholder> {
    override val id: Id<DbPlaceholder> get() = error("unreachable")
  }
}