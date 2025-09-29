FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/AuctionBackend-1.0-SNAPSHOT.jar app.jar

ENV PORT=7070
EXPOSE 7070

CMD ["java", "-jar", "app.jar"]