FROM maven:3.9.12-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml maven-settings.xml ./
COPY src ./src
RUN mvn -B -s maven-settings.xml -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system suishi && useradd --system --gid suishi suishi
COPY --from=build /app/target/suishi-backend-0.1.0.jar app.jar
USER suishi
EXPOSE 8080
ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-jar","app.jar"]
