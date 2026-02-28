FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY ticket-booking-common/pom.xml ticket-booking-common/
COPY user-service/pom.xml user-service/
COPY ticket-service/pom.xml ticket-service/
RUN mvn dependency:go-offline -B

COPY ticket-booking-common ticket-booking-common
RUN mvn -pl ticket-booking-common -am clean install -DskipTests

COPY user-service user-service
RUN mvn -pl user-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/user-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
