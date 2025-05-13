package app.cash.backfila.service.deletion

import com.google.inject.Provides
import java.time.Clock
import javax.inject.Singleton
import misk.inject.KAbstractModule

class DeleteByNotificationModule : KAbstractModule() {
    override fun configure() {
        bind<DeleteByNotificationService>().asEagerSingleton()
        bind<DeleteByNotificationHelper>().asEagerSingleton()
    }

    @Provides @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}