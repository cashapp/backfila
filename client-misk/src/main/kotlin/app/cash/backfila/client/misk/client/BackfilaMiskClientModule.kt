package app.cash.backfila.client.misk.client

import app.cash.backfila.client.BackfilaApi
import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.client.ForBackfila
import app.cash.backfila.client.misk.internal.GetNextBatchRangeAction
import app.cash.backfila.client.misk.internal.PrepareBackfillAction
import app.cash.backfila.client.misk.internal.RunBatchAction
import com.google.inject.Key
import com.google.inject.Provides
import misk.client.TypedHttpClientModule
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import retrofit2.Retrofit
import retrofit2.converter.wire.WireConverterFactory

/**
 * Use this to connect to a real Backfila service in staging or production. You will also need to
 * install a [MiskBackfillModule].
 */
class BackfilaMiskClientModule : KAbstractModule() {
  override fun configure() {
    install(
      TypedHttpClientModule(
        kclass = BackfilaApi::class,
        name = "backfila",
        retrofitBuilderProvider = getProvider(
          Key.get(Retrofit.Builder::class.java, ForBackfila::class.java)
        )
      )
    )

    install(WebActionModule.create<PrepareBackfillAction>())
    install(WebActionModule.create<GetNextBatchRangeAction>())
    install(WebActionModule.create<RunBatchAction>())
  }

  @Provides @ForBackfila internal fun wireRetrofitBuilder() =
    Retrofit.Builder().addConverterFactory(WireConverterFactory.create())
}
