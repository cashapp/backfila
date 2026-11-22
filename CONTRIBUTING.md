# Contributing

If you would like to contribute code to this project you can do so through GitHub by
forking the repository and sending a pull request.

When submitting code, please make every effort to follow existing conventions
and style in order to keep the code as readable as possible.

Before your code can be accepted into the project you must also sign the
[Individual Contributor License Agreement (CLA)][1].

## Binary compatibility

The published client libraries have their public ABI tracked by the
[binary-compatibility-validator][2] plugin. Each module's public API is committed under
`<module>/api/<module>.api`, and `gradle apiCheck` (run as part of `gradle check` and in CI)
fails the build when the compiled public API drifts from those baselines.

If you intentionally change a client library's public API, regenerate the baselines and commit the
result:

```
gradle apiDump
```

## Database schema

The service's schema is defined by the versioned files in
`service/src/main/resources/migrations/`, which the service applies in order. To change the
schema, add a new versioned file; never edit one that has already shipped.

The service jar also ships the same schema in declarative form: one `CREATE TABLE` per table in
`service/src/main/resources/mysql/schema/backfila/`, for tools that manage schema declaratively.
Each file is the MySQL 8 `SHOW CREATE TABLE` output for its table once every versioned file has
been applied, without the `AUTO_INCREMENT` counter. `DeclarativeSchemaTest` (run as part of
`gradle check` and in CI) fails when the two disagree. After adding a versioned file, regenerate
the declarative files and commit the result. Like the tests, this needs MySQL 8 on
localhost:3306 accepting `root` with an empty password:

```
gradle :service:regenerateDeclarativeSchema
```


 [1]: https://spreadsheets.google.com/spreadsheet/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1
 [2]: https://github.com/Kotlin/binary-compatibility-validator