FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY services/notification-service/target/notification-service-1.0.0.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]