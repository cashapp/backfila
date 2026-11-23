package app.cash.backfila.ui

/** Browsers send "on" for a checked box and nothing for an unchecked one; links may also use "off" or "false". */
internal fun parseCheckbox(value: String?): Boolean? = when (value?.trim()?.lowercase()) {
  null, "" -> null
  "off", "false" -> false
  else -> true
}
