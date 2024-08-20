package app.cash.backfila.client.misk.menustack

import app.cash.backfila.client.misk.MenuItem
import app.cash.backfila.client.misk.hibernate.HibernateBackfillModule
import app.cash.backfila.client.stat.StaticDatasourceBackfillModule
import com.google.inject.Provides
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque
import javax.inject.Singleton
import misk.inject.KAbstractModule

class MenuStackModule : KAbstractModule() {
  override fun configure() {
    install(HibernateBackfillModule.create<MenuStackDbBackfill>())
    install(StaticDatasourceBackfillModule.create<MenuStackParametersBackfill>())
  }

  @Provides @MenuStack @Singleton
  fun provideMenuStack(): BlockingDeque<MenuItem> = LinkedBlockingDeque()
}

annotation class MenuStack
