FROM eclipse-temurin:26.0.2_10-jre-ubi10-minimal

WORKDIR /app

COPY target/greeting-operator-0.1.0-SNAPSHOT.jar app.jar

USER 10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
