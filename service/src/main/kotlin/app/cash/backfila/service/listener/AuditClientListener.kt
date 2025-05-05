package app.cash.backfila.service.listener

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.ui.pages.BackfillShowAction
import javax.inject.Inject
import misk.audit.AuditClient
import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.hibernate.load

internal class AuditClientListener @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val auditClient: AuditClient,
  private val backfilaConfig: BackfilaConfig,
) : BackfillRunListener {
  override fun runStarted(id: Id<DbBackfillRun>, user: String) {
    val (backfillName, serviceName, description) = transacter.transaction { session ->
      val run = session.load<DbBackfillRun>(id)
      AuditEventInputs(run.registered_backfill.name, serviceName(run), "Backfill started by $user ${dryRunPrefix(run)}${nameAndId(run)}")
    }
    auditClient.logEvent(
      target = backfillName,
      description = description,
      requestorLDAP = user,
      applicationName = serviceName,
      detailURL = idUrl(id),
    )
  }

  override fun runPaused(id: Id<DbBackfillRun>, user: String) {
    val (backfillName, serviceName, description) = transacter.transaction { session ->
      val run = session.load<DbBackfillRun>(id)
      AuditEventInputs(run.registered_backfill.name, serviceName(run), "Backfill paused by $user ${dryRunPrefix(run)}${nameAndId(run)}")
    }
    auditClient.logEvent(
      target = backfillName,
      description = description,
      requestorLDAP = user,
      applicationName = serviceName,
      detailURL = idUrl(id),
    )
  }

  override fun runErrored(id: Id<DbBackfillRun>) {
    val (backfillName, serviceName, description) = transacter.transaction { session ->
      val run = session.load<DbBackfillRun>(id)
      AuditEventInputs(run.registered_backfill.name, serviceName(run), "Backfill paused due to error ${dryRunPrefix(run)}${nameAndId(run)}")
    }
    auditClient.logEvent(
      target = backfillName,
      description = description,
      automatedChange = true,
      applicationName = serviceName,
      detailURL = idUrl(id),
    )
  }

  override fun runCompleted(id: Id<DbBackfillRun>) {
    val (backfillName, serviceName, description) = transacter.transaction { session ->
      val run = session.load<DbBackfillRun>(id)
      AuditEventInputs(run.registered_backfill.name, serviceName(run), "Backfill completed ${dryRunPrefix(run)}${nameAndId(run)}")
    }
    auditClient.logEvent(
      target = backfillName,
      description = description,
      automatedChange = true,
      applicationName = serviceName,
      detailURL = idUrl(id),
    )
  }

  override fun runCancelled(id: Id<DbBackfillRun>, user: String) {
    val (backfillName, serviceName, description) = transacter.transaction { session ->
      val run = session.load<DbBackfillRun>(id)
      AuditEventInputs(run.registered_backfill.name, serviceName(run), "Backfill cancelled by $user ${dryRunPrefix(run)}${nameAndId(run)}")
    }
    auditClient.logEvent(
      target = backfillName,
      description = description,
      requestorLDAP = user,
      applicationName = serviceName,
      detailURL = idUrl(id),
    )
  }

  private fun serviceName(run: DbBackfillRun) = if (run.service.variant == "default") {
    run.service.registry_name
  } else {
    "${run.service.registry_name}/${run.service.variant}"
  }

  private fun nameAndId(run: DbBackfillRun) =
    "[service=${serviceName(run)}][backfill=${run.registered_backfill.name}]" +
      "[id=${run.id}]" + if (run.service.slack_channel != null) {
      "[slackChannel=${run.service.slack_channel}]"
    } else {
      ""
    }

  private fun dryRunPrefix(run: DbBackfillRun) =
    if (run.dry_run) {
      "[dryRun=true]"
    } else {
      ""
    }

  private fun idUrl(id: Id<DbBackfillRun>): String = backfilaConfig.web_url_root + BackfillShowAction.path(id.id)

  private data class AuditEventInputs(
    val backfillName: String,
    val serviceName: String,
    val description: String,
  )
}
