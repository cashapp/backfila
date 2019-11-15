package app.cash.backfila.client.misk

import javax.inject.Qualifier

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class ClientMiskService