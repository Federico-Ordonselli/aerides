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
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
