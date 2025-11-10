FROM gradle:8.7-jdk21 AS builder
WORKDIR /workspace
COPY build.gradle.kts settings.gradle.kts /workspace/
COPY src /workspace/src
RUN gradle clean bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/backend.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/backend.jar"]
