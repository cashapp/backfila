package app.cash.backfila.ui.components

import app.cash.backfila.ui.SLACK_CHANNEL_NAME
import app.cash.backfila.ui.SLACK_CHANNEL_URL
import kotlinx.html.TagConsumer

fun TagConsumer<*>.AlertSlackHelp() {
  AlertInfoHighlight(
    "Questions? Concerns? Contact us on Slack.",
    SLACK_CHANNEL_NAME,
    SLACK_CHANNEL_URL,
    spaceAbove = true,
  )
}