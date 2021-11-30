package app.cash.backfila.client.misk.hibernate

import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder

class PrimaryKeyCursorAdapterModule<PK : Any>(
  private val primaryKeyType: Class<PK>,
  private val primaryKeyCursorAdapter: PrimaryKeyCursorAdapter<PK>,
) : AbstractModule() {
  override fun configure() {
    MapBinder.newMapBinder(
      binder(),
      object : TypeLiteral<Class<*>>() {},
      object : TypeLiteral<PrimaryKeyCursorAdapter<*>>() {},
    ).addBinding(primaryKeyType)
      .toInstance(primaryKeyCursorAdapter)
  }

  companion object {
    inline fun <reified PK : Any> create(
      primaryKeyCursorAdapter: PrimaryKeyCursorAdapter<PK>,
    ): PrimaryKeyCursorAdapterModule<PK> {
      return PrimaryKeyCursorAdapterModule(PK::class.java, primaryKeyCursorAdapter)
    }
  }
}
