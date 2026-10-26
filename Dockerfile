# สร้างภาพเดียวจบ: คอมไพล์ด้วย Maven แล้วรันด้วย JRE 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 app
WORKDIR /app
COPY --from=build /src/target/taxbook-1.0.0.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Bangkok"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
