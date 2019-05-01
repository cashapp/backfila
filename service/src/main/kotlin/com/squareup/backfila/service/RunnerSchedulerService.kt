package com.squareup.backfila.service

import com.google.common.collect.Sets
import com.google.common.util.concurrent.AbstractExecutionThreadService
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.inject.Key
import misk.DependentService
import misk.hibernate.SchemaMigratorService
import misk.inject.toKey
import misk.logging.getLogger
import java.util.Random
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a background thread, looking for unleased backfills to run.
 *
 * When one is found, a runner thread is created to work on the backfill.
 * On shutdown, the runners are stopped so the leases can be eagerly released.
 */
@Singleton
class RunnerSchedulerService @Inject constructor(
  @ForBackfilaScheduler private val runnerExecutorService: ListeningExecutorService,
  private val leaseHunter: LeaseHunter
) : AbstractExecutionThreadService(), DependentService {
  override val consumedKeys: Set<Key<*>> = setOf(
      SchemaMigratorService::class.toKey(BackfilaDb::class))
  override val producedKeys: Set<Key<*>> = setOf(Key.get(RunnerSchedulerService::class.java))

  /**
   * List of runners maintained so we can tell them to shut down.
   * Added to by the service thread, removed from by the executor threads.
   */
  private val runners: MutableSet<BackfillRunner> = Sets.newConcurrentHashSet()

  @Volatile private var running = false
  private val random = Random()

  override fun startUp() {
    running = true
  }

  override fun run() {
    while (running) {
      val newRunners = leaseHunter.hunt()

      newRunners.forEach(::addRunner)

      Thread.sleep(1000L + random.nextInt(5000))
    }
  }

  override fun triggerShutdown() {
    running = false
  }

  override fun shutDown() {
    logger.info { "Shutting down backfila scheduler" }

    // Tell runners to clean up
    for (runner in runners) {
      logger.info { "Stopping runner: ${runner.name}" }
      try {
        runner.stop()
      } catch (e: Exception) {
        logger.info(e) { "Exception stopping runner: ${runner.name}" }
      }
    }

    runnerExecutorService.shutdown()
    val awaited = runnerExecutorService.awaitTermination(10, TimeUnit.SECONDS)

    if (awaited) {
      logger.info { "Runners shut down" }

      // As the runners complete they remove themselves from the set,
      // so it should be empty after waiting for completion.
      if (runners.isNotEmpty()) {
        logger.warn {
          "Runners not empty at shutdown, is there a bug? names: ${runners.map { it.name }}"
        }
      }
    } else {
      logger.info {
        "Timed out waiting for runners to complete. names: ${runners.map { it.name }}"
      }
    }
  }

  private fun addRunner(runner: BackfillRunner) {
    logger.info { "Leased backfill: ${runner.name}" }
    runners.add(runner)
    runnerExecutorService.submit {
      try {
        runner.work()
      } catch (e: Exception) {
        logger.info(e) { "Runner had uncaught exception: ${runner.name}" }
      }
      runners.remove(runner)
      logger.info { "Runner removed: ${runner.name}" }
    }
  }

  companion object {
    private val logger = getLogger<RunnerSchedulerService>()
  }
}

