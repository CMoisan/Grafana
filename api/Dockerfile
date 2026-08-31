# =============================================================================
# BUILD MULTI-STAGE
# Interet : l'image finale ne contient ni Maven, ni le code source, ni le cache
# .m2. On passe de ~800 Mo a ~220 Mo, et surtout la surface d'attaque chute.
# Bonus : vous n'avez PAS besoin de Maven installe sur votre poste.
# =============================================================================

# ------------------------------- STAGE 1 : build -----------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# On copie d'abord le pom SEUL, puis on telecharge les dependances.
# Docker met cette couche en cache : tant que le pom ne change pas, un rebuild
# apres modification du code ne retelecharge rien. Gain : 2 min -> 15 s.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# -DskipTests ici car les tests tournent en CI, pas dans le build d'image.
RUN mvn -B clean package -DskipTests

# ------------------------------ STAGE 2 : runtime ----------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Utilisateur non-root : exige par toute PodSecurity policy serieuse.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /build/target/orders-api-*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

# =============================================================================
# OPTIONS JVM - a comprendre absolument pour parler perf avec un client
# =============================================================================
# MaxRAMPercentage : la JVM lit la limite MEMOIRE DU CGROUP (donc le
#   `resources.limits.memory` du pod) et prend 75% pour le heap. Le reste sert
#   au metaspace, aux threads et aux buffers natifs. Fixer -Xmx en dur dans un
#   conteneur est une erreur classique : le jour ou on change la limite k8s,
#   la JVM ne le voit pas et le pod se fait OOMKill.
#
# UseG1GC : GC par defaut, bon compromis debit/pause pour une API web.
#
# ExitOnOutOfMemoryError : en cas d'OOM heap, on tue le process. k8s redemarre
#   le pod, ce qui est BIEN mieux qu'une JVM zombie qui repond en 30 s.
#   Ce redemarrage sera visible dans Grafana via kube_pod_container_status_restarts.
#
# -XX:+UseContainerSupport est actif par defaut depuis Java 10, on le laisse
#   explicite pour la pedagogie.
# =============================================================================
ENV JAVA_OPTS="-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+UseG1GC \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom"

# `exec` en avant : le process Java devient PID 1 et recoit directement SIGTERM.
# Sans `exec`, le shell est PID 1, avale SIGTERM, et k8s finit par SIGKILL a la
# fin de la grace period -> arret brutal, requetes coupees, erreurs 502 dans vos
# dashboards a chaque deploiement. C'est un grand classique d'incident.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
