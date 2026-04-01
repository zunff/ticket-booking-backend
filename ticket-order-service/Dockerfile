FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ticket-order-service-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
