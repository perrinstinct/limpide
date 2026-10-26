# Étape 1 : compilation, puis découpage du jar en couches.
# Les tests ne tournent pas ici : la CI les a déjà joués avant de construire l'image.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/limpide-api-*.jar extract --layers --launcher --destination target/extracted

# Étape 2 : uniquement le JRE et l'application, de la couche la plus stable à la plus changeante.
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 limpide
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER limpide
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
