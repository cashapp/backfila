# client-dynamodb

Backfila backend client implementation to backfill a DynamoDB datastore.

There are a couple features that help keep the costs down when performing a DynamoDB datastore backfill.

This backfill client performs what should usually be a single pass of the dynamo datastore. However, in order
to do that it **does not provide accurate counts of the records processed** but instead reports the number of segments
processed instead.

This client will also by default require that the DynamoDb table has the `PROVISIONED` billing mode as `ON_DEMAND` can
get very expensive otherwise.

The code is the source of truth for this client. Keep that in mind. Always refer to the code for implementation details.
