package app.cash.backfila.client.misk

import jakarta.inject.Qualifier

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class ClientMiskService
