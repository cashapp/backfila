# client-dynamodb-v2

Backfila backend client implementation to backfill a DynamoDB datastore 
using the v2 api.

Each `RunBatch` service call evaluates **at most `batch_size` items** and passes at most that
many matching items to the backfill. Batch size is an upper bound, not a guaranteed number of items.
The client performs one DynamoDB scan per call. DynamoDB can stop at its 1 MB page limit or the
end of the current segment before reaching `batch_size`. Filtering can further reduce the number
of matching items, including to zero. For example, `batch_size=100` can process fewer than 100 items
even when more items remain in the table. See the [DynamoDB Scan documentation](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Scan.html).

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
