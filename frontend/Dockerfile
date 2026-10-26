# Étape 1 : compilation de l'appli Angular.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Étape 2 : Caddy sert les fichiers statiques ; sa configuration vient de infra/Caddyfile.
FROM caddy:2-alpine
COPY --from=build /app/dist/limpide-web/browser /srv
