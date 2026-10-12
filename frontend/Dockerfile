# Multi-stage build for NHL Stats Frontend
FROM node:24-alpine AS build
# The image's bundled npm lags behind npm's own dependency fixes (issue #58); the
# newest npm 11 carries the fixed ones it has. Remaining ones: .trivyignore.yaml.
RUN apk upgrade --no-cache && npm install -g npm@11 && npm cache clean --force

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Fix permissions on node_modules binaries
RUN chmod -R +x node_modules/.bin || true

# Optional public links baked into the bundle (Vite inlines them at build time)
ARG VITE_WEB3FORMS_ACCESS_KEY=
ARG VITE_DONATION_URL_SEK=
ARG VITE_DONATION_URL_EUR=
ARG VITE_DONATION_URL_USD=

# Build the application
RUN npm run build

# Runtime stage with nginx
FROM nginx:alpine

# Take Alpine security fixes released since the base image was built.
RUN apk upgrade --no-cache

# Copy built assets from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/security-headers.conf

# The official nginx entrypoint renders this before starting nginx. No TCP
# peers are trusted by default; production must set Caddy's exact address.
ENV CADDY_TRUSTED_PROXY=unix:
ENV NGINX_ENVSUBST_FILTER=^CADDY_TRUSTED_PROXY$
COPY nginx/realip.conf.template /etc/nginx/templates/realip.conf.template

# Expose port
EXPOSE 3000

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
