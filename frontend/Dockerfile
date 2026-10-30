# Multi-stage build for NHL Stats Frontend
FROM node:24.22-alpine AS build
RUN apk upgrade --no-cache

WORKDIR /app

# Copy package files
COPY package*.json ./
# Patch npm itself using the same version as CI.
RUN npm install --global "$(node -p "require('./package.json').packageManager")" && npm cache clean --force
# Every npm release so far bundles undici older than 6.29.1, which fixes
# CVE-2026-109869 and CVE-2026-109924. Only npm's node-gyp uses it (range ^6.25.0),
# so swap in the fixed release. The build fails once npm bundles it, so remove this then.
ARG NPM_UNDICI=6.29.1
RUN modules=/usr/local/lib/node_modules/npm/node_modules \
 && node -e "const [b, f] = process.argv.slice(1), s = require('$modules/semver'); \
      if (!s.lt(b, f)) { console.error('npm bundles undici ' + b + '; remove the undici swap'); process.exit(1); }" \
      "$(node -p "require('$modules/undici/package.json').version")" "$NPM_UNDICI" \
 && npm pack --silent --pack-destination /tmp "undici@$NPM_UNDICI" \
 && rm -rf "$modules/undici" && mkdir "$modules/undici" \
 && tar -xzf "/tmp/undici-$NPM_UNDICI.tgz" -C "$modules/undici" --strip-components=1 \
 && rm "/tmp/undici-$NPM_UNDICI.tgz" && npm cache clean --force

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
RUN npm run build && npm run check:targets

# Runtime stage with nginx
FROM nginx:alpine

# Take Alpine security fixes released since the base image was built.
RUN apk upgrade --no-cache

# Copy built assets from build stage
COPY --from=build /app/dist /usr/share/nginx/html
# Server configuration is built separately from the public site output.
COPY --from=build /app/.build/nginx/spa-routes.conf /etc/nginx/spa-routes.conf

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
