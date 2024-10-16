package app.cash.backfila.client.sqldelight.persistence

import javax.inject.Qualifier

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class HockeyDataDb
