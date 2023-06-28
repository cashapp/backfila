# client-misk

Implements a backfila client for misk apps. This uses misk and guice to register the implementing service with backfila 
and provides endpoints for backfila to call into your service to run the backfills. Use this in conjunction with client 
implementations such as `client-misk-hibernate`, `client-dynamodb` or `client-jooq`.

The code is the source of truth for this client. Keep that in mind. Always refer to the code for implementation details.
