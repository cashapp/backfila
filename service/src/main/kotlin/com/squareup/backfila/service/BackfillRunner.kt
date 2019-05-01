package com.squareup.backfila.service

import misk.logging.getLogger

class BackfillRunner(val name: String) {
  @Volatile private var running = true

  fun stop() {
    // TODO cancel futures (after 5s timeout? that needs another thread)
    // TODO cleanup lease
    running = false
  }

  fun work() {
    logger.info { "Runner starting: $name" }
    while (running) {
      logger.info { "Runner looping: $name" }
      Thread.sleep(1000L)
    }
    logger.info { "Runner complete: $name" }
  }

  companion object {
    private val logger = getLogger<BackfillRunner>()
  }
}