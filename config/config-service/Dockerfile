FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY config/config-service/target/config-service-1.0.0.jar app.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]