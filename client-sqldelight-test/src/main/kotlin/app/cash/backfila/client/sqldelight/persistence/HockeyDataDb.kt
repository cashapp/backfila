package app.cash.backfila.client.sqldelight.persistence

import jakarta.inject.Qualifier

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class HockeyDataDb
