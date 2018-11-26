# backfila

This is a sample app that makes use of Misk. All commands below assume you're in the root directory
of this repository.

## Building
Build backfila:

```
  $ ./gradlew clean shadowJar
```

## Run the Service

### From the command line

```
  $ java -jar service/build/libs/service.jar
```

### From IntelliJ
  Right-click on `BackfilaService.kt` and select `Run`

### From Docker

#### Building

Build a Docker image of backfila:

```
  $ docker build -t backfila-0.0.1 service
```

#### Running locally

Visit [Docker for Mac](https://docs.docker.com/docker-for-mac/install/) to install Docker on a Mac for testing.

Run backfila in Docker locally:
```
  $ docker run -p 8080:8080 backfila-0.0.1
```

Confirm backfila works with curl:

```
  $ curl --data '{"message": "hello"}' -H 'Content-Type: application/json' http://localhost:8080/ping
```

## Polyrepo

Visit [Polyrepo Readme](https://git.sqcorp.co/projects/CASH/repos/cash/browse/README.md) for the polyrepo docs

## Other examplars

See [cash-urlshortener](https://git.sqcorp.co/projects/CASH/repos/cash-urlshortener/browse) for an example of how to set up a MySql database
