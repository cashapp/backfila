package app.cash.backfila.mcp

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.ui.pages.BackfillShowAction
import jakarta.inject.Inject
import jakarta.inject.Singleton

/** Builds absolute links into the Backfila dashboard, or null when `web_url_root` isn't configured. */
@Singleton
internal class DashboardLinks @Inject constructor(
  config: BackfilaConfig,
) {
  private val root: String? = config.web_url_root.trimEnd('/').takeIf { it.isNotBlank() }

  val home: String? get() = root?.let { "$it/" }

  fun backfillRun(id: Long): String? = root?.let { it + BackfillShowAction.path(id) }
}
