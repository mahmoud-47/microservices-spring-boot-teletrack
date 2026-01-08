FROM eclipse-temurin:21-jre-alpine

# Install curl for healthcheck
RUN apk add --no-cache curl

WORKDIR /app

# Copy the JAR file
COPY config/config-service/target/config-service-1.0.0.jar app.jar

# Copy the config files into the container
COPY config/config-service/src/main/resources/config /app/config

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]