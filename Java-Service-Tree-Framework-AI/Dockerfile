# MultiAgent MVP 백엔드 — 소스에서 bootJar 를 빌드해 실행한다.
# aipm-net 안에서 aipm-ai:8080 으로만 노출하고, 외부 진입은 프론트(nginx)가 담당한다.
FROM gradle:8.13-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jdk

VOLUME /tmp

COPY docker-entrypoint.sh docker-entrypoint.sh
COPY --from=build /workspace/build/libs/*.jar javaServiceTreeFramework.jar

RUN chmod +x /docker-entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["sh","/docker-entrypoint.sh"]
CMD ["start"]
