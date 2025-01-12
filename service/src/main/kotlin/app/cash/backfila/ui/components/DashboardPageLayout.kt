package app.cash.backfila.ui.components

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import jakarta.inject.Inject
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.main
import kotlinx.html.script
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.hotwire.buildHtml
import misk.scope.ActionScoped
import misk.tailwind.Link
import misk.tailwind.pages.MenuSection
import misk.tailwind.pages.Navbar
import misk.web.HttpCall
import misk.web.ResponseBody
import misk.web.dashboard.HtmlLayout
import okio.BufferedSink
import wisp.deployment.Deployment

/**
 * Builds dashboard UI for index homepage.
 *
 * Must be called within a Web Action.
 */
class DashboardPageLayout @Inject constructor(
  private val callerProvider: ActionScoped<MiskCaller?>,
  private val deployment: Deployment,
  private val clientHttpCall: ActionScoped<HttpCall>,
  private val config: BackfilaConfig,
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

  private fun setNewBuilder() = apply { newBuilder = true }

  fun newBuilder(): DashboardPageLayout = DashboardPageLayout(
    callerProvider = callerProvider,
    deployment = deployment,
    clientHttpCall = clientHttpCall,
    config = config,
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
            src = "/static/js/auto_reload_controller.js"
          }
          script {
            type = "module"
            src = "/static/js/search_bar_controller.js"
          }
        },
      ) {
        div("min-h-full") {
          // Uses Misk's Navbar with sidebar
          Navbar(
            appName = "Backfila",
            deployment = deployment,
            homeHref = "/",
            menuSections = buildMenuSections(
              currentPath = path,
            ),
            sortedMenuLinks = false,
          ) {
            div("py-10") {
              main {
                div("mx-auto max-w-7xl sm:px-6 lg:px-8") {
                  // TODO remove when new UI is stable and preferred
                  UseOldUIAlert()

                  block()

                  AlertSupport(config.support_button_label, config.support_button_url)
                }
              }
            }
          }
        }
      }
    }
  }

  fun buildHtmlResponseBody(block: TagConsumer<*>.() -> Unit): ResponseBody = object : ResponseBody {
    override fun writeTo(sink: BufferedSink) {
      sink.writeUtf8(build(block))
    }
  }

  private fun buildMenuSections(
    currentPath: String,
  ): List<MenuSection> {
    return transacter.transaction { session ->
      val runningBackfillsForCaller = queryFactory.newQuery<BackfillRunQuery>()
        .createdByUser(callerProvider.get()!!.user!!)
        .state(BackfillState.RUNNING)
        .orderByUpdatedAtDesc()
        .apply {
          maxRows = 10
        }
        .list(session)

      // TODO get services from REgistry for user || group backfills by service
      val services = runningBackfillsForCaller.groupBy { it.service }

      listOf(
        MenuSection(
          title = "Backfila",
          links = listOf(
            Link(
              label = "Home",
              href = "/",
              isSelected = currentPath == "/",
            ),
            Link(
              label = "Services",
              href = "/services/",
              isSelected = currentPath == "/services/",
            ),
            Link(
              label = "Backfills",
              href = "/backfills/",
              isSelected = currentPath == "/backfills/",
            ),
          ),
        ),
      ) + if (services.isNotEmpty()) {
        listOf(
          MenuSection(
            title = "Your Services",
            links = services.map { (service, backfills) ->
              val variant = if (service.variant == "default") "" else service.variant
              Link(
                label = service.registry_name,
                href = "/services/${service.registry_name}/$variant",
                isSelected = currentPath.startsWith("/services/${service.registry_name}/$variant"),
              )
            },
          ),
        )
      } else {
        listOf()
      } + if (runningBackfillsForCaller.isNotEmpty()) {
        listOf(
          MenuSection(
            title = "Your Backfills",
            links = runningBackfillsForCaller.map { backfill ->
              Link(
                label = backfill.service.registry_name + " #" + backfill.id,
                href = "/backfills/${backfill.id}",
                isSelected = currentPath.startsWith("/backfills/${backfill.id}"),
              )
            },
          ),
        )
      } else {
        listOf()
      }
    }
  }

  companion object {
  }
}
