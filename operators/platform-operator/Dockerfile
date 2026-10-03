FROM eclipse-temurin:26-jre

WORKDIR /app

COPY target/platform-operator-0.1.0-SNAPSHOT.jar /app/app.jar

USER 10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
