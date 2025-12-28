FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy JAR file from build
COPY target/football-scheduler-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/api/schedule/status/1 || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]