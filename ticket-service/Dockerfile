FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ticket-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms512m", "-Xmx768m", "-Xss512k",
"-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-XX:MinRAMPercentage=50",
"-XX:+UseZGC","-jar", "app.jar"]

