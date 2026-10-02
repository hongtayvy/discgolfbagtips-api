# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
# The Maven wrapper pins the same Maven version CI uses, rather than whatever a base image ships.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S bagtips && adduser -S bagtips -G bagtips
COPY --from=build /build/target/*.jar app.jar
USER bagtips
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
