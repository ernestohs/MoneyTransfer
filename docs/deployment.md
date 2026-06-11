# Deployment Guide

## Docker Files

- `Dockerfile` builds a production-style Java 17 image with a non-root app user and `/actuator/health` health check.
- `docker-compose.yml` builds and runs the packaged app with PostgreSQL for local use.
- `docker-compose.dev.yml` runs the app from the mounted source tree for active development.
- `docker-compose.cloud.yml` runs a prebuilt registry image against externally supplied cloud database settings.
- `.env.example` documents local Compose defaults.
- `.dockerignore` keeps build output, local environment files, IDE files, and Git metadata out of image builds.

## Build And Deploy A Container

Build a production image:

```bash
docker build -t moneytransfer:latest .
```

Run that image against a managed PostgreSQL database:

```bash
docker run --rm -p 8080:8080 \
  -e DATABASE_URL='jdbc:postgresql://db.example.com:5432/moneytransfer' \
  -e DATABASE_USERNAME='moneytransfer' \
  -e DATABASE_PASSWORD='change-me' \
  -e DATABASE_DRIVER='org.postgresql.Driver' \
  -e ALLOW_ANONYMOUS='false' \
  -e WEBHOOK_SIGNING_SECRET='change-me' \
  moneytransfer:latest
```

For Compose-capable cloud hosts, push the image to a registry and use `docker-compose.cloud.yml`:

```bash
APP_IMAGE='ghcr.io/your-org/moneytransfer:latest' \
DATABASE_URL='jdbc:postgresql://db.example.com:5432/moneytransfer' \
DATABASE_USERNAME='moneytransfer' \
DATABASE_PASSWORD='change-me' \
WEBHOOK_SIGNING_SECRET='change-me' \
docker compose -f docker-compose.cloud.yml up -d
```

Cloud deployments should provide a managed PostgreSQL database, set `ALLOW_ANONYMOUS=false`, and configure Spring Security JWT issuer or JWK settings for real authentication.

## Configuration

The main settings are environment-variable driven:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:h2:mem:moneytransfer...` | JDBC URL |
| `DATABASE_USERNAME` | `sa` | Database user |
| `DATABASE_PASSWORD` | empty | Database password |
| `DATABASE_DRIVER` | `org.h2.Driver` | JDBC driver |
| `ALLOW_ANONYMOUS` | `true` | Allows local unauthenticated API access |
| `WEBHOOK_SIGNING_SECRET` | `dev-webhook-secret` | HMAC secret for outbox payload signatures |
| `JAVA_OPTS` | empty | Optional JVM flags used by the Docker image |
| `PORT` | `8080` | Host port used by `docker-compose.cloud.yml` |
| `APP_IMAGE` | required for cloud Compose | Registry image used by `docker-compose.cloud.yml` |

For production, set `ALLOW_ANONYMOUS=false` and configure a real Spring Security JWT decoder through issuer or JWK settings, for example:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.com/
```

Owner identity is derived from JWT `tenant_id` when present, otherwise from JWT `sub`.
