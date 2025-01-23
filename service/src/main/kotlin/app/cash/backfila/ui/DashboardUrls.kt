package app.cash.backfila.ui

object DashboardUrls {
  fun app(appName: String) = "/app/$appName"
  fun createDeploy(appName: String) = "/app/$appName/deploy"
  fun deploy(appName: String, deployName: String) = "/app/$appName/deploy/$deployName"
  fun setMinimalCommitTimestamp(appName: String) = "/app/$appName/set-minimal-commit-timestamp"
}
