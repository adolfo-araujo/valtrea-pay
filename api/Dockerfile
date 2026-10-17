# Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S valtrea && adduser -S valtrea -G valtrea
USER valtrea
WORKDIR /app
COPY --from=build /build/target/valtrea-pay-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
