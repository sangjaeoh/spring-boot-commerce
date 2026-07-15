# app-migration·app-api 공용 멀티스테이지 빌드. compose의 build.target으로 앱을 고른다.
# 빌드팩(bootBuildImage) 대신 Dockerfile을 쓴 이유: compose가 Gradle 태스크를 대신 실행할 수
# 없어 "클론 → docker compose 명령 하나" 목표를 빌드팩으로는 만족할 수 없다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
# 캐시 마운트로 Gradle 배포판·의존성 다운로드를 재빌드 간 재사용한다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :module-apps:app-migration:bootJar :module-apps:app-api:bootJar

FROM eclipse-temurin:25-jre AS app-migration
COPY --from=build /workspace/module-apps/app-migration/build/libs/app-migration.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:25-jre AS app-api
COPY --from=build /workspace/module-apps/app-api/build/libs/app-api.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
