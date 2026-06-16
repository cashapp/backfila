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


 [1]: https://spreadsheets.google.com/spreadsheet/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1
 [2]: https://github.com/Kotlin/binary-compatibility-validator