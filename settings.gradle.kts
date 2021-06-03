include("client")
include("client-misk")
include("client-misk-dynamodb")
include("client-misk-hibernate")
include("client-misk-jooq")
include("client-misk-testing")
include("service")

val localSettings = file("local.settings.gradle")
if (localSettings.exists()) {
  apply(from = localSettings)
}
