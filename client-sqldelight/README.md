# client-sqldelight

Backfila client backend implementation for SQLDelight. It consists of two parts. This client 
which manages the backfills in your service and `client-sqldelight-gradle-plugin` that helps you 
generate the queries that the client needs.

If you end up using this client directly without the plugin you will need to be abundantly cautious 
to ensure that the queries collaborate correctly.

See `client-sqldelight-test` for examples.