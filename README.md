# Backfila

Backfila is a service that manages backfill state, calling into other services to do batched work.

Note: some instructions here are out of date

## Hermit

[Hermit](https://cashapp.github.io/hermit/) is used to manage build dependencies like local Java and Gradle versions.

Install [Hermit shell hooks](https://cashapp.github.io/hermit/usage/shell/?h=shell) or run the following manually.

```
$ . ./bin/activate-hermit
```

## Building
Build backfila:

```
$ gradle clean shadowJar
```

## Run the Service

### Run the Misk-Web Build
In order to ensure the web UI is built and able to be served by the service, run the following command:

```
$ npm install -g @misk/cli && miskweb ci-build -e
```

### From the command line

```
$ java -jar service/build/libs/service.jar
```

### From IntelliJ
Right-click on `BackfilaDevelopmentService.kt` and select `Run`

### From Docker

#### Building

Build a Docker image of backfila:

```
$ docker build -t backfila-0.0.1
```

#### Running locally

Visit [Docker for Mac](https://docs.docker.com/docker-for-mac/install/) to install Docker on a Mac for testing.

Run backfila in Docker locally:
```
$ docker run -p 8080:8080 backfila-0.0.1
```

Visit the UI at http://localhost:8080/

## Client
The backfila client must be installed in your services to expose their backfill code.
It also provides the batching mechanism and templates for common types of backfills.

## Connectors
Connectors can be installed to provide a way to connect to your services.
The included default connectors are HTTPS and Envoy. Add custom connectors using Guice map binding.

Gradle
--------

```kotlin
implementation("app.cash.backfila:backfila-client:0.1.0")
implementation("app.cash.backfila:backfila-service-lib:0.1.0")
```

License
--------

    Copyright 2019 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
