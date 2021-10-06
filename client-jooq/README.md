# client-jooq

Jooq backfila client backend implementation.

If you are changing the test schema, the jooq classes needs to be regenerated, 
you can do that by running this command from the root directory 
of the project. Please note that requires docker, as the script will bring up a mysql 
db in a docker container and generate jooq classes from there.

```
./client-jooq/jooq-test-regenerate.sh
```