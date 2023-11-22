package app.cash.backfila.ui.components

import kotlinx.html.div

// fun TagConsumer<*>.CollapsibleMonitorList(category: MonitorClass?, monitors: List<Pair<String, String?>>, isOpen: Boolean = false) {
//   li("overflow-hidden rounded-md bg-white shadow") {
//     ToggleContainer(
//       isOpen = isOpen,
//       buttonText = "${monitors.size}",
//       menuBlock = {
//         category?.let { MonitorCategory(category) }
//           ?: p {
//             +"ERROR: missing monitor category definition"
//           }
//       },
//     ) {
//       ul("divide-y divide-gray-100") {
//         role = "list"
//
//         monitors.map {
//           li("flex items-center justify-between gap-x-6 py-5") {
//             div("min-w-0") {
//               div("flex items-start gap-x-3") {
//                 p("text-sm font-mono font-semibold leading-6 text-gray-900") {
//                   +it.first
//                   if (it.second != null) {
//                     +" (${it.second})"
//                   }
//                 }
//               }
//             }
//             div("flex flex-none items-center gap-x-4") {
//               a(classes = "rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block") {
//                 href =
//                   "https://github.com/squareup/cash-datadog/blob/main/modules/platform/${directory.getValue(it.first)}/${it.first}"
//                 +"""View on Github"""
//               }
//             }
//           }
//         }
//       }
//     }
//   }
// }
//
// val directory = mapOf(
//   "plasma-flow-availability" to "slos",
//   "plasma-requirement-availability" to "slos",
// ).withDefault { "monitors" }
