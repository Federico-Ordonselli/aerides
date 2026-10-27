# --- Build stage: compile the Angular app ---
FROM node:24-alpine AS build
WORKDIR /workspace

# Install dependencies first: this layer is cached until package-lock.json changes
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Runtime stage: static files served by nginx ---
FROM nginx:1.29-alpine
# The image's entrypoint fills ${BACKEND_URL} and ${BACKEND_HOST} in templates/ with envsubst at startup and
# writes the result to conf.d/. Only variables set in the environment are replaced, so $host & co. stay intact.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
# docker compose: the backend service on its own port, the browser's Host header, and nginx is the edge,
# so it writes the client IP and scheme itself (infra/apps.tf sets the values for a managed ingress)
ENV BACKEND_URL=http://backend:8080 \
    BACKEND_HOST=\$host \
    FORWARDED_FOR=\$remote_addr \
    FORWARDED_PROTO=\$scheme
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
