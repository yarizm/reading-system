# Stage 1: Build the Spring Boot application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Use Aliyun mirror for Maven dependencies (国内网络环境)
COPY docker-maven-settings.xml /root/.m2/settings.xml

# Fix-07: Copy pom.xml and download dependencies offline to cache them
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src/ src/
RUN mvn package -DskipTests -q

# Stage 2: Run with minimal JRE
FROM eclipse-temurin:17-jre
WORKDIR /app

# Fix-08: Create non-root user and assign permissions
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy the built jar (deterministic name via maven finalName=app)
COPY --from=build /app/target/app.jar app.jar

# Create upload directory and change ownership
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

USER appuser
EXPOSE 8090

# Fix-09: Add JVM memory constraints for containers
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
