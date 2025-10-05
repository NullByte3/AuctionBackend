FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update && apt-get install -y gettext-base

COPY --from=build /app/target/AuctionBackend-1.0-SNAPSHOT.jar app.jar
COPY --from=build /app/src/main/resources/hibernate.cfg.xml.template .
COPY entrypoint.sh .

RUN chmod +x entrypoint.sh

ENV PORT=7070
EXPOSE 7070

ENTRYPOINT ["/app/entrypoint.sh"]

CMD ["java", "-cp", ".:app.jar", "club.nullbyte3.auction.Application"]