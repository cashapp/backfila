include("backfila-embedded")
include("bom")
include("client")
include("client-base")
include("client-dynamodb")
include("client-dynamodb-v2")
include("client-jooq")
include("client-misk")
include("client-misk-hibernate")
include("client-s3")
include("client-sqldelight")
include("client-sqldelight-test")
include("client-static")
include("client-testing")
include("service")
include("service-self-backfill")

// These are going to be deleted as they are replaced by miskless versions.
include("client-misk-dynamodb")
include("client-misk-jooq")
include("client-misk-static")

val localSettings = file("local.settings.gradle")
if (localSettings.exists()) {
  apply(from = localSettings)
}
