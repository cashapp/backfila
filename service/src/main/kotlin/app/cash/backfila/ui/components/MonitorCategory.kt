package app.cash.backfila.ui.components

import com.squareup.cash.monitorcheckup.heuristics.MonitorClass
import kotlinx.html.TagConsumer
import kotlinx.html.p

fun TagConsumer<*>.MonitorCategory(category: MonitorClass) {
  val text = when (category) {
    MonitorClass.LOGGING -> "Rate-limited logs make debugging harder. Service owners should know when they are losing logs."
    MonitorClass.CERT_EXPIRY -> "Services that depend on Square SSL certificates need to be alerted when those certificates are expiring."
    MonitorClass.RPC_INCOMING -> "RPC incoming monitors alert service owners when the service is responding to callers with abnormal error rates."
    MonitorClass.RPC_OUTGOING -> "RPC outgoing monitors alert service owners when the service is observing abnormal error rates from dependencies."
    MonitorClass.FRANKLIN -> "Franklin enforces rate limiting through service quotas. Services that depend on Franklin need to know when they've reached those quotas."
    MonitorClass.DYNAMODB -> "TODO"
    MonitorClass.MYSQL -> "Services that depend on MySQL should monitor their DB to determine if they're reaching capacity."
    MonitorClass.JOBQUEUE -> "Async jobs can fail or be delayed. Service owners should know when jobs are not being processed."
    MonitorClass.ELASTICACHE -> "TODO"
    MonitorClass.PLASMA_FLOW_OWNER -> "Plasma Flow monitors keeping track of errors, failures, or general availability."
    MonitorClass.PLASMA_REQUIREMENT_OWNER -> "Plasma Requirement monitors keeping track of errors, failures, or general availability."
    MonitorClass.KUBERNETES_HEALTH -> "Kubernetes health monitors alert service owners when the service is in a broken state."
    MonitorClass.KUBERNETES_USAGE -> "Kubernetes usage monitors alert service owners when their service is nearing a configured limit."
  }
  p {
    +text
  }
}
