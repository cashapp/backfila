include("backfila-embedded")
include("client")
include("client-base")
include("client-dynamodb")
include("client-jooq")
include("client-misk")
include("client-misk-hibernate")
include("client-static")
include("client-testing")
include("service")

// These are going to be deleted as they are replaced by miskless versions.
include("client-misk-dynamodb")
include("client-misk-jooq")
include("client-misk-static")

val localSettings = file("local.settings.gradle")
if (localSettings.exists()) {
  apply(from = localSettings)
}
