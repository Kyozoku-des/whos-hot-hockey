# The files a release runs with on the server, versioned with the application images
# (issue #23). The deploy script copies them out; nothing runs this image.
# Build from the repository root:
#   docker build -f infra/server/bundle.Dockerfile .
FROM scratch
COPY compose.yaml /bundle/compose.yaml
COPY caddy/Caddyfile /bundle/caddy/Caddyfile
COPY monitoring/alloy/config.alloy /bundle/monitoring/alloy/config.alloy
