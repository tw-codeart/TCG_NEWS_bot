# Build the application JAR with Maven before building this runtime image.
ARG JAVA_IMAGE=eclipse-temurin:17-jre-jammy@sha256:8993f1aed8b25fcea7a7047a7949c1866fa558fc6830d938c22c4f13b26be9d7
FROM ${JAVA_IMAGE}

WORKDIR /app
RUN mkdir -p /app/data && chown -R 10001:10001 /app
COPY --chown=10001:10001 target/tcg-news-bot-0.1.0-SNAPSHOT.jar /app/app.jar

USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
