package com.squareup.backfila.dashboard

import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import java.lang.management.ManagementFactory
import javax.inject.Inject

class GetJvmThreadsAction @Inject constructor() : WebAction {
  @Get("/debug/threads")
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  @Authenticated
  fun threads(): String {
    val dump = StringBuilder()
    val threadMXBean = ManagementFactory.getThreadMXBean()
    val threadInfos = threadMXBean.getThreadInfo(threadMXBean.allThreadIds, 100)
    for (threadInfo in threadInfos) {
      dump.append('"')
      dump.append(threadInfo.threadName)
      dump.append("\" ")
      val state = threadInfo.threadState
      dump.append("\n   java.lang.Thread.State: ")
      dump.append(state)
      val stackTraceElements = threadInfo.stackTrace
      for (stackTraceElement in stackTraceElements) {
        dump.append("\n        at ")
        dump.append(stackTraceElement)
      }
      dump.append("\n\n")
    }
    return dump.toString()
  }
}
