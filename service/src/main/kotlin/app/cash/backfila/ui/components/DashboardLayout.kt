package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.main
import kotlinx.html.script
import misk.tailwind.Link
import misk.tailwind.pages.MenuSection
import misk.tailwind.pages.Navbar
import misk.web.dashboard.HtmlLayout
import wisp.deployment.getDeploymentFromEnvironmentVariable

fun TagConsumer<*>.DashboardLayout(
  title: String,
  path: String,
  block: TagConsumer<*>.() -> Unit = {},
) {
  val deployment = getDeploymentFromEnvironmentVariable()

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

private fun buildMenuSections(
  currentPath: String,
) = listOf(
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
    links = listOf(
      Link(
        label = "FineDining #0034",
        href = "/services/",
        isSelected = currentPath.startsWith("/backfill/"),
      ),
      Link(
        label = "FineDining #0067",
        href = "/backfill/",
        isSelected = currentPath.startsWith("/backfill/"),
      ),
    ),
  ),
)
