# app-migration·app-api 공용 멀티스테이지 빌드. compose의 build.target으로 앱을 고른다.
# 빌드팩(bootBuildImage) 대신 Dockerfile을 쓴 이유: compose가 Gradle 태스크를 대신 실행할 수
# 없어 "클론 → docker compose 명령 하나" 목표를 빌드팩으로는 만족할 수 없다.
# Java 버전 정본: gradle/libs.versions.toml [versions] java. 카탈로그를 못 읽는 Dockerfile이라 태그 리터럴(25)을 두되 정본과 일치시킨다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
# 캐시 마운트로 Gradle 배포판·의존성 다운로드를 재빌드 간 재사용한다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :module-apps:app-migration:bootJar :module-apps:app-api:bootJar

# 두 앱 공통 런타임 베이스 — non-root 사용자로 실행한다(권한 상승 표면 축소). Java 태그(25)는 위 build 스테이지와 같은 정본을 따른다.
FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system app && useradd --system --no-create-home --gid app app
USER app

FROM runtime AS app-migration
COPY --from=build /workspace/module-apps/app-migration/build/libs/app-migration.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS app-api
COPY --from=build /workspace/module-apps/app-api/build/libs/app-api.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
