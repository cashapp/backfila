FROM gradle:jdk17-jammy AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17.0.6_10-jre-jammy
RUN mkdir /usr/local/backfila
COPY --from=build /home/gradle/src/service/build/libs/service-*-SNAPSHOT.jar /usr/local/backfila/service.jar

WORKDIR /usr/local/backfila
RUN groupadd -g 999 appuser && \
    useradd -r -u 999 -g appuser appuser
USER 999

CMD ["java", "-jar", "/usr/local/backfila/service.jar"]