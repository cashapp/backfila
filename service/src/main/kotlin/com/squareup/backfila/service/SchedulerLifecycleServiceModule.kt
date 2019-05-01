package com.squareup.backfila.service

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule

class SchedulerLifecycleServiceModule : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to(RunnerSchedulerService::class.java)
  }
}