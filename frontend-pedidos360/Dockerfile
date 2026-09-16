# Build multi-stage: compila el Angular con Node y sirve el resultado con Nginx.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

FROM nginx:1.27-alpine
# Azure AD exige que el redirect URI de una SPA sea https:// (o
# http://localhost). Sin dominio propio ni certificado pagado, se genera uno
# autofirmado en el build -- el navegador va a pedir aceptar la advertencia
# de certificado no confiable la primera vez, pero cumple el requisito de
# esquema que pide Azure AD.
RUN apk add --no-cache openssl && \
    mkdir -p /etc/nginx/ssl && \
    openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
      -keyout /etc/nginx/ssl/selfsigned.key \
      -out /etc/nginx/ssl/selfsigned.crt \
      -subj "/CN=pedidos360"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend-pedidos360/browser /usr/share/nginx/html
EXPOSE 443
