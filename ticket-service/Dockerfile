FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ticket-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
