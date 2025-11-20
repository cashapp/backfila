package app.cash.backfila.client.s3.internal

import app.cash.backfila.client.s3.ForS3BackendV2
import app.cash.backfila.client.s3.S3DatasourceBackfill
import app.cash.backfila.client.s3.shim.S3Service
import com.google.inject.Injector
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class S3DatasourceBackendV2 @Inject constructor(
  injector: Injector,
  @ForS3BackendV2 backfills: MutableMap<String, KClass<out S3DatasourceBackfill<*, *>>>,
  @ForS3BackendV2 s3Service: S3Service,
) : AbstractS3DatasourceBackend(injector, backfills, s3Service)
