FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY discovery/discovery-service/target/discovery-service-1.0.0.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]