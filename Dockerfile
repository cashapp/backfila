FROM gradle:6.7.1-jdk11-openj9 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle shadowJar --no-daemon

FROM openjdk:11
RUN mkdir /usr/local/backfila
COPY --from=build /home/gradle/src/service/build/libs/service-*-SNAPSHOT.jar /usr/local/backfila/service.jar

WORKDIR /usr/local/backfila
RUN groupadd -g 999 appuser && \
    useradd -r -u 999 -g appuser appuser
USER 999

CMD ["java", "-jar", "/usr/local/backfila/service.jar"]