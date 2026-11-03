# Imagen de Keycloak de LIV: Keycloak con el tema liv (ADR 0024, tarea T08 de la feature 001).
# Uso, desde backend/:  docker build -t liv-identity-keycloak identity
# Imágenes fijadas por digest (doc. 03 §4.1). Al subir Keycloak, sube también el compose de dev/ y repite la
# verificación de keycloak-config-cli (identity/README.md).

# 1. Hoja de estilos del tema: Tailwind con los tokens del paquete liv-components, con versión exacta.
FROM node:22.21.1-alpine@sha256:0340fa682d72068edf603c305bfbc10e23219fb0e40df58d9ea4d6f33a9798bf AS estilos
WORKDIR /theme
COPY theme/package.json theme/package-lock.json theme/.npmrc ./
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY theme/liv ./liv
RUN npm run build

# 2. Keycloak optimizado para PostgreSQL, con salud y métricas en el puerto de gestión.
FROM quay.io/keycloak/keycloak:26.8.1@sha256:3f3c5f8e03c614774b4bd9173b88391fefa4153bfc010d8012d59ad825377332 AS keycloak
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true
COPY --from=estilos /theme/liv/login /opt/keycloak/themes/liv/login
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.1@sha256:3f3c5f8e03c614774b4bd9173b88391fefa4153bfc010d8012d59ad825377332
COPY --from=keycloak /opt/keycloak/ /opt/keycloak/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
