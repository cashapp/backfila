#!/usr/bin/env bash
function getContainerHealth {
  docker inspect --format "{{.State.Health.Status}}" $1
}

function waitContainer {
  while STATUS=$(getContainerHealth $1); [ $STATUS != "healthy" ]; do
    if [ $STATUS == "unhealthy" ]; then
      echo "Failed!"
      exit -1
    fi
    printf .
    lf=$'\n'
    sleep 1
  done
  printf "$lf"
}

echo "starting mysql in docker"
docker run --rm \
  --name backfila-jooq-codegen \
  -e MYSQL_DATABASE=backfila-jooq-codegen \
  -e MYSQL_ROOT_HOST=% \
  -e MYSQL_ROOT_PASSWORD=root \
  -p 3500:3306 \
  -d mysql/mysql-server:5.7 \
  --sql-mode=""
echo "waiting for mysql to start up"
waitContainer backfila-jooq-codegen

./gradlew -p client-jooq generateJooq

docker stop backfila-jooq-codegen