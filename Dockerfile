# ── Build: Maven + JDK 21 ───────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ── Run: JRE 21 + the jar ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home iam \
 && mkdir -p /app/uploads/avatars /app/keys \
 && chown -R iam /app
COPY --from=build /src/target/ms-iam-*.jar app.jar
USER iam
EXPOSE 3001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
