# Rebuild the standard Caddy distribution with patched Go and x/net versions.
# Keep go.mod/go.sum in sync when updating Caddy or its security dependencies.
FROM golang:1.27.3-alpine AS build
ENV GOTOOLCHAIN=local
RUN apk upgrade --no-cache
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY main.go ./
RUN CGO_ENABLED=0 go build -mod=readonly -trimpath -o /out/caddy .

FROM caddy:2.11.7-alpine
RUN apk upgrade --no-cache
COPY --from=build /out/caddy /usr/bin/caddy
