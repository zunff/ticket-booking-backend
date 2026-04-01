FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ticket-gateway-service-1.0.0.jar app.jar
EXPOSE 9000
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
