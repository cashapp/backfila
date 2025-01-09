package app.cash.backfila.ui.components

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import jakarta.inject.Inject
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.main
import kotlinx.html.script
import misk.MiskCaller
import misk.config.AppName
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.hotwire.buildHtml
import misk.scope.ActionScoped
import misk.tailwind.Link
import misk.tailwind.pages.MenuSection
import misk.tailwind.pages.Navbar
import misk.web.HttpCall
import misk.web.dashboard.DashboardHomeUrl
import misk.web.dashboard.DashboardNavbarItem
import misk.web.dashboard.DashboardTab
import misk.web.dashboard.HtmlLayout
import wisp.deployment.Deployment

/**
 * Builds dashboard UI for index homepage.
 *
 * Must be called within a Web Action.
 */
class DashboardPageLayout @Inject constructor(
  private val allHomeUrls: List<DashboardHomeUrl>,
  @AppName private val appName: String,
  private val allNavbarItem: List<DashboardNavbarItem>,
  private val allTabs: List<DashboardTab>,
  private val callerProvider: ActionScoped<MiskCaller?>,
  private val deployment: Deployment,
  private val clientHttpCall: ActionScoped<HttpCall>,
  private val getBackfillRunsAction: GetBackfillRunsAction,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) {
  private var newBuilder = false
  private var headBlock: TagConsumer<*>.() -> Unit = {}
  private var title: String = "Backfila"

  private val path by lazy {
    clientHttpCall.get().url.encodedPath
  }
  private val dashboardHomeUrl by lazy {
    allHomeUrls.firstOrNull { path.startsWith(it.url) }
  }
  private val homeUrl by lazy {
    dashboardHomeUrl?.url ?: "/"
  }

  private fun setNewBuilder() = apply { newBuilder = true }

  fun newBuilder(): DashboardPageLayout = DashboardPageLayout(
    allHomeUrls = allHomeUrls,
    appName = appName,
    allNavbarItem = allNavbarItem,
    allTabs = allTabs,
    callerProvider = callerProvider,
    deployment = deployment,
    clientHttpCall = clientHttpCall,
    getBackfillRunsAction = getBackfillRunsAction,
    transacter = transacter,
    queryFactory = queryFactory,
  ).setNewBuilder()

  fun title(title: String) = apply {
    this.title = title
  }

  fun headBlock(block: TagConsumer<*>.() -> Unit) = apply { this.headBlock = block }

  @JvmOverloads
  fun build(block: TagConsumer<*>.() -> Unit = { }): String {
    check(newBuilder) {
      "You must call newBuilder() before calling build() to prevent builder reuse."
    }
    newBuilder = false

    return buildHtml {
      HtmlLayout(
        appRoot = "/",
        title = title,
        // TODO only use play CDN in development, using it always for demo purporses to avoid UI bugs
//    playCdn = deployment.isLocalDevelopment,
        playCdn = true,
        headBlock = {
          script {
            type = "module"
            src = "/static/js/autocomplete_controller.js"
          }
          script {
            type = "module"
            src = "/static/js/search_bar_controller.js"
          }
        },
      ) {
        div("min-h-full") {
          if (true) {
            // Uses Misk's Navbar with sidebar
            Navbar(
              appName = "Backfila",
              deployment = deployment,
              homeHref = "/",
              menuSections = buildMenuSections(
                currentPath = path,
              ),
            ) {
              div("py-10") {
                main {
                  div("mx-auto max-w-7xl sm:px-6 lg:px-8") {
                    // TODO remove when new UI is stable and preferred
                    UseOldUIAlert()
                    block()
                  }
                }
              }
            }
          } else {
            // Old UI
            NavBar(path)
            div("py-10") {
              main {
                div("mx-auto max-w-7xl sm:px-6 lg:px-8") {
                  // TODO remove when new UI is stable and preferred
                  UseOldUIAlert()
                  block()
                }
              }
            }
          }
        }
      }
    }
  }

  private fun buildMenuSections(
    currentPath: String,
  ): List<MenuSection> {
    return transacter.transaction { session ->
      val runningBackfills = queryFactory.newQuery<BackfillRunQuery>()
        .createdByUser(callerProvider.get()!!.user!!)
        .state(BackfillState.RUNNING)
        .orderByUpdatedAtDesc()
        .list(session)

      // TODO get services from REgistry for user || group backfills by service

      listOf(
        MenuSection(
          title = "Backfila",
          links = listOf(
            Link(
              label = "Services",
              href = "/services/",
              isSelected = currentPath.startsWith("/services/"),
            ),
            Link(
              label = "Backfills",
              href = "/backfills/",
              isSelected = currentPath.startsWith("/backfills/"),
            ),
          ),
        ),
        MenuSection(
          title = "Your Services",
          links = listOf(
            Link(
              label = "Fine Dining",
              href = "/services/?q=FineDining",
              isSelected = currentPath.startsWith("/services/?q=FindDining"),
            ),
          ),
        ),
        MenuSection(
          title = "Your Backfills",
          links = runningBackfills.map { backfill ->
            Link(
              label = backfill.service.registry_name + " #" + backfill.id,
              href = "/backfills/${backfill.id}",
              isSelected = currentPath.startsWith("/backfills/${backfill.id}"),
            )
          },
        ),
      )
    }
  }

  companion object {
  }
}
