# client-sqldelight

Backfila client backend implementation for the SQLDelight ORM.

We think this might involve two parts. One part here that uses some kind of interface to point out 
the queries that we need to do the backfill and a part in sqldelight that makes it harder to screw 
up the queries. Something along the lines of templates might be good? OR perhaps guardrails?

SqlDelight has the idea of plugins so we could create a plugin that enforces guardrails.
