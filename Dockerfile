# Stage 1: Build the Spring Boot application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Use Aliyun mirror for Maven dependencies (国内网络环境)
COPY docker-maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src/ src/
RUN mvn package -DskipTests -q

# Stage 2: Run with minimal JRE
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar
COPY --from=build /app/target/reading-system-0.0.1-SNAPSHOT.jar app.jar

# Create upload directory
RUN mkdir -p /app/uploads

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
