# client-base

Contains the internal implementation details for a Backfila Client. This should only be depended on
by Backfila client implementations. Those clients will include this only as an implementation
dependency since these APIs are private and are never not be available to downstream client consumers.

The code is the source of truth for this client. Keep that in mind. Always refer to the code for implementation details.
