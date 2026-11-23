package app.cash.backfila.ui.components

import app.cash.backfila.dashboard.RegisteredBackfill
import app.cash.backfila.ui.pages.BackfillCreateAction
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.time
import kotlinx.html.title
import kotlinx.html.ul

private const val REGISTRATION_COLUMNS = "grid grid-cols-[minmax(12rem,1fr)_12rem_12rem] gap-x-6"
private val registrationTimestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
  .withZone(ZoneOffset.UTC)

internal fun TagConsumer<*>.RegisteredBackfillsList(
  backfills: List<RegisteredBackfill>,
  service: String,
  variantOrBlank: String?,
) {
  div("overflow-x-auto py-5") {
    div("min-w-[44rem]") {
      div("$REGISTRATION_COLUMNS px-6 pb-3 text-sm font-medium text-gray-500") {
        span { +"Backfill" }
        span("text-right") {
          title = "First registered with Backfila for this service and variant in this environment."
          +"Created at"
        }
        span("text-right") {
          title = "When the current backfill configuration was registered with Backfila."
          +"Updated at"
        }
      }
      ul("grid grid-cols-1 gap-6") {
        role = "list"
        backfills.forEach { backfill ->
          li("registration rounded-lg bg-white shadow") {
            a(classes = "$REGISTRATION_COLUMNS items-baseline gap-y-1 p-6") {
              val variantOrBackfillNameOrId = variantOrBlank.orEmpty().ifBlank { backfill.name }
              href = BackfillCreateAction.path(
                service = service,
                variantOrBackfillNameOrId = variantOrBackfillNameOrId,
                backfillNameOrIdOrBlank = if (variantOrBackfillNameOrId == backfill.name) "" else backfill.name,
              )
              h3("min-w-0 truncate text-sm font-medium text-gray-900") {
                title = backfill.name
                +backfill.name
              }
              time("whitespace-nowrap text-right text-sm tabular-nums text-gray-500") {
                dateTime = backfill.createdAt.toString()
                +registrationTimestampFormat.format(backfill.createdAt)
              }
              val updatedAt = backfill.updatedAt
              if (updatedAt != null) {
                time("whitespace-nowrap text-right text-sm tabular-nums text-gray-500") {
                  dateTime = updatedAt.toString()
                  +registrationTimestampFormat.format(updatedAt)
                }
              } else {
                span("whitespace-nowrap text-right text-sm text-gray-500") { +"—" }
              }
              if (backfill.parameterNames.isNotEmpty()) {
                p("col-span-3 truncate text-sm text-gray-500") {
                  +"Custom Parameters: ${backfill.parameterNames.joinToString(", ")}"
                }
              }
            }
          }
        }
      }
    }
  }
}
