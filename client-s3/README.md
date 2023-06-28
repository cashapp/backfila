# client-s3

Backfila client backend implementation to backfill all s3 objects in a bucket that match a prefix. 
You specify a bucket via `getBucket`. You specify the prefix either statically by `staticPrefix` or by 
overriding `getPrefix`. Each s3 object is a separate partition. You must define a record strategy to 
divide up the s3 object into batches.
* Object names must be less than 45 characters after the prefix.

You must ensure that the bucket is available to the service using this client. The s3 calls are made
from this service. 

The partition name will be the object name after the prefix. The range values reported are the byte seek 
in the s3 object. The record counts are also reported in bytes rather than strict records. Scan size is also in bytes.

In tests install the `FakeS3Module` and fill `FakeS3Service` with your test files. You have two options, 
either add each file in code or point `FakeS3Service` to a resource path and it will load everything 
under that path. Use `S3CdnModule` in real instances and provide an `AmazonS3` annotated with `@ForS3Backend`.

The code is the source of truth for this client. Keep that in mind. Always refer to the code for implementation details.
