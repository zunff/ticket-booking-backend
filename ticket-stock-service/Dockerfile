FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ticket-stock-service-1.0.0.jar app.jar
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
