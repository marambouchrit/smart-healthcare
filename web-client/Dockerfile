# syntax=docker/dockerfile:1
# Client web Angular — image construite depuis la racine du dépôt (le référentiel vient de contracts/seed, D49, D51)
#   docker build -f web-client/Dockerfile -t smart-healthcare/web-client .

# 1) Construction : Node (même version que le poste de développement, D48), dépendances figées par package-lock.json
FROM node:20.19.5-slim AS construction
WORKDIR /src
COPY contracts/seed contracts/seed
COPY web-client/package.json web-client/package-lock.json web-client/
WORKDIR /src/web-client
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY web-client/ ./
# prebuild : copier-seed.mjs copie le référentiel de ../contracts/seed dans public/seed
RUN npm run build

# 2) Service : nginx sert les fichiers statiques ; la gateway est la seule porte d'entrée (aucun port publié)
FROM nginx:1.30.7-alpine3.24
COPY web-client/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=construction /src/web-client/dist/web-client/browser /usr/share/nginx/html
EXPOSE 4200
