package com.squareup.cash.monitorcheckup.ui

import app.cash.backfila.ui.pages.ServiceAction

// TODO add tests!
data class PathBuilder(
  val frame: String? = null,
  val boolean: Boolean? = null,
  val query: String? = null,
  val path: String? = null,
  val limit: Int? = null,
  val page: Int? = null,
  val service: String? = null,
  /** Could be comma separated list of tokens */
  val token: String? = null,
  val sortColumn: String? = null,
  val sortDesc: Boolean? = null,
  val platform: String? = null,
  val project: String? = null,
  val client_key: String? = null,
  val version: String? = null,
  val locale: String? = null,
) {
  fun build(): String = StringBuilder().apply {
    append("/")
    path?.removePrefix("/")?.let {
      append(it.split("{").first())

      if (path == ServiceAction.PATH && service != null) {
        append(service)
        append("/")
      }
    }
    platform?.removePrefix("/")?.let { append("/$it") }
    project?.removePrefix("/")?.let { append("/$it") }
    client_key?.removePrefix("/")?.let { append("/$it") }
    version?.removePrefix("/")?.let { append("/$it") }
    locale?.removePrefix("/")?.let { append("/$it") }

    if (!this.contains("?") && this.last() != '&') append("?")
    boolean?.let { append("$BooleanParam=$it&") }
    limit?.let { append("$LimitParam=$it&") }
    page?.let { append("$PageParam=$it&") }
    sortDesc?.let { append("$SortDescParam=$it&") }
    if (!service.isNullOrBlank()) append("$ServiceParam=$service&")
    if (!frame.isNullOrBlank()) { append("$FrameParam=$frame&") }
    if (!query.isNullOrBlank()) { append("$SearchParam=$query&") }
    if (!token.isNullOrBlank()) { append("$TokenParam=$token&") }
    if (!sortColumn.isNullOrBlank()) { append("$SortColumnParam=$sortColumn&") }
  }.toString().removeSuffix("?").removeSuffix("&")

  companion object {
    const val FrameParam = "frame"
    const val BooleanParam = "boolean"
    const val SearchParam = "q"
    const val LimitParam = "limit"
    const val PageParam = "p"
    const val ServiceParam = "s"
    const val TokenParam = "t"
    const val SortColumnParam = "sc"
    const val SortDescParam = "sd"
  }
}
