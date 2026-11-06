# Imagen del frontend de LIV (feature 001, T21): la aplicación Angular compilada, servida por nginx sin
# privilegios (doc. 01 §2 y doc. 03 §4.1). Node solo se usa para compilar y no llega a la imagen final.
# Uso, desde frontend/:  docker build -t liv-frontend .
# Imágenes fijadas por digest (doc. 03 §4.1). Al subir una versión, sube también el digest y repite la
# verificación de frontend/README.md («Imagen»).

# 1. Compilación con las versiones exactas del lock.
FROM node:22.21.1-alpine@sha256:0340fa682d72068edf603c305bfbc10e23219fb0e40df58d9ea4d6f33a9798bf AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund
COPY . .
RUN npm run build

# 2. nginx sin privilegios (usuario 101, puerto 8080), solo con los estáticos y su configuración.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/liv-frontend/browser /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
