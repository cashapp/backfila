package app.cash.backfila.development.mcdees

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.client.misk.client.BackfilaMiskClientModule
import app.cash.backfila.client.s3.S3DatasourceBackfillModule
import app.cash.backfila.client.s3.shim.FakeS3Module
import app.cash.backfila.client.s3.shim.FakeS3Service
import app.cash.backfila.client.stat.StaticDatasourceBackfillModule
import com.google.common.util.concurrent.AbstractIdleService
import javax.inject.Inject
import javax.inject.Singleton
import misk.ServiceModule
import misk.inject.KAbstractModule

internal class McDeesServiceModule(
  private val variant: String,
  private val port: Int,
) : KAbstractModule() {
  override fun configure() {
    // Development Service Config

    // Backfill Config
    install(BackfilaMiskClientModule())
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "http://localhost:$port/",
          slack_channel = "#test",
          variant = variant,
        ),
      ),
    )
    install(StaticDatasourceBackfillModule.create<BurgerFlippingBackfill>())
    install(StaticDatasourceBackfillModule.create<BootsAndCatsBackfill>())

    // For S3 Backfills
    install(FakeS3Module())
    install(ServiceModule<LoadS3McDeesDataService>())
    install(S3DatasourceBackfillModule.create<RestockingBackfill>())
  }
}

@Singleton
internal class LoadS3McDeesDataService : AbstractIdleService() {
  @Inject lateinit var fakeS3: FakeS3Service

  override fun startUp() {
    fakeS3.loadResourceDirectory("mcdees")
  }

  override fun shutDown() {
  }
}
