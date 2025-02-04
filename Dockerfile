FROM eclipse-temurin:17-jdk
COPY target/rdb-0.0.1-SNAPSHOT.jar /app/rdb.jar
COPY src/main/resources/application.properties /app/
ENV CLASSPATH=/app/rdb.jar
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/rdb.jar"]