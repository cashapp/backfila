package app.cash.backfila.ui.components

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.ui.BackfilaDashboard
import app.cash.backfila.ui.pages.IndexAction
import jakarta.inject.Inject
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.role
import kotlinx.html.script
import kotlinx.html.span
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.hotwire.buildHtml
import misk.scope.ActionScoped
import misk.tailwind.Link
import misk.tailwind.icons.Heroicons
import misk.tailwind.icons.heroicon
import misk.tailwind.pages.MenuSection
import misk.tailwind.pages.Navbar
import misk.web.HttpCall
import misk.web.ResponseBody
import misk.web.dashboard.DashboardTab
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
  private val allTabs: List<DashboardTab>,
) {
  private var newBuilder = false
  private var headBlock: TagConsumer<*>.() -> Unit = {}
  private var title: String = "Backfila"
  private var breadcrumbLinks: List<Link> = listOf()

  private val path by lazy {
    clientHttpCall.get().url.encodedPath
  }

  private val backfilaLinks by lazy {
    allTabs.filter { it.dashboardAnnotationKClass == BackfilaDashboard::class }
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
    allTabs = allTabs,
  ).setNewBuilder()

  fun title(title: String) = apply {
    this.title = title
  }

  fun headBlock(block: TagConsumer<*>.() -> Unit) = apply { this.headBlock = block }

  fun breadcrumbLinks(vararg links: Link?) = apply { this.breadcrumbLinks = links.toList().filterNotNull() }

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
                // TODO maybe make max-w wider
                div("mx-auto max-w-7xl sm:px-6 lg:px-8") {
                  // TODO remove when new UI is stable and preferred
                  UseOldUIAlert()

                  if (breadcrumbLinks.isNotEmpty()) {
                    Breadcrumbs(breadcrumbLinks)
                  }

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
          ) + backfilaLinks.filter { it.menuCategory == "Backfila" }.map { tab ->
            Link(
              label = tab.menuLabel,
              href = tab.menuUrl,
              isSelected = currentPath.startsWith(tab.menuUrl),
            )
          },
        ),
      ) + if (backfilaLinks.filterNot { it.menuCategory == "Backfila" }.isNotEmpty()) {
        backfilaLinks.filterNot { it.menuCategory == "Backfila" }.groupBy { it.menuCategory }.map { (category, tabs) ->
          MenuSection(
            title = category,
            links = tabs.map { tab ->
              Link(
                label = tab.menuLabel,
                href = tab.menuUrl,
                isSelected = currentPath.startsWith(tab.menuUrl),
              )
            },
          )
        }
      } else {
        listOf()
      } + if (services.isNotEmpty()) {
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

  // TODO upstream this to Misk
  private fun TagConsumer<*>.Breadcrumbs(links: List<Link>) {
    nav("flex") {
      attributes["aria-label"] = "Breadcrumb"
      ol("flex items-center space-x-4") {
        role = "list"
        li {
          div {
            a(classes = "text-gray-400 hover:text-gray-500") {
              href = IndexAction.PATH

              heroicon(Heroicons.OUTLINE_HOME)
              span("sr-only") { +"""Home""" }
            }
          }
        }
        links.forEach {
          li {
            div("flex items-center") {
              // TODO upstream chevron_right and use instead
              heroicon(Heroicons.MINI_ARROW_LONG_RIGHT)
              a(classes = "ml-4 text-sm font-medium text-gray-500 hover:text-gray-700") {
                href = it.href
                +it.label
              }
            }
          }
        }
      }
    }
  }

  companion object {
  }
}
