package com.squareup.backfila.service

import misk.hibernate.Transacter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaseHunter @Inject constructor(
  @BackfilaDb private val transacter: Transacter
) {
  fun hunt(): Set<BackfillRunner> {
    return setOf()
  }
}