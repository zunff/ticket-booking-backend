FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ticket-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
