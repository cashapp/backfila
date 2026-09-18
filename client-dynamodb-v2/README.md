# client-dynamodb-v2

Backfila backend client implementation to backfill a DynamoDB datastore 
using the v2 api.

Each `RunBatch` service call performs one DynamoDB scan with `batch_size` as its limit.
The limit bounds items evaluated before filtering; the scan may return fewer matches or none.
If the scan returns a `LastEvaluatedKey`, the client returns it in `remaining_batch_range`
so a later call can continue the same segment. Segments remain the scheduling unit.
`checkpointSegmentProgressAfter` is deprecated and has no effect; each call returns after one scan.

There are a couple features that help keep the costs down when performing a DynamoDB datastore backfill.

This backfill client performs what should usually be a single pass of the dynamo datastore. However, in order 
to do that it does not provide accurate counts of the records processed but instead reports the number of segments 
processed instead. 

This client will also by default require that the DynamoDb table has the `PROVISIONED` billing mode as `ON_DEMAND` can 
get very expensive otherwise.

The code is the source of truth for this client. Keep that in mind. Always refer to the code for implementation details.
