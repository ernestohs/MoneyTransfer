# MoneyTransfer

Professional Spring Boot payment API for creating accounts, transferring money, reading ledger-backed transaction history, and operating account/transfer state safely.

## What This Service Provides

- Public REST API with `/accounts`, `/transfers`, and actuator routes.
- PostgreSQL-ready persistence through Spring Data JPA and Flyway migrations.
- Local development fallback using in-memory H2.
- Transactional double-entry ledger writes for completed transfers.
- Idempotent transfer creation through the `Idempotency-Key` header.
- Structured error responses with correlation IDs.
- Account freeze, unfreeze, and close operations.
- Transfer cancellation and reversal.
- Audit log and outbox rows written atomically with account/transfer changes.
- Actuator health and metrics endpoints.
- Swagger/OpenAPI UI dependency included.

## Requirements

- Docker and Docker Compose for the recommended local and deployment workflows.
- Java 17 for native, non-Docker development.
- Gradle wrapper included in this repo.
- PostgreSQL for production-style native runs.
- `curl` for the examples below

## Quick Start With Docker

This is the recommended way to run the API locally because it starts both the Spring Boot service and PostgreSQL.

```bash
cp .env.example .env
docker compose up --build
```

The API will be available at:

```text
http://localhost:8080
```

Health check:

```bash
curl -i http://localhost:8080/actuator/health
```

Stop the stack:

```bash
docker compose down
```

Remove the local PostgreSQL volume when you want a fresh database:

```bash
docker compose down -v
```

## Docker Development

Use the dev Compose file when you want source changes mounted into the container and `./gradlew bootRun` running inside a JDK image:

```bash
docker compose -f docker-compose.dev.yml up
```

The dev stack uses the same PostgreSQL settings as the default stack, but stores database data in a separate `postgres-dev-data` volume. Gradle cache files are written to the repo's ignored `.gradle` directory.

## Docker Files

- `Dockerfile` builds a production-style Java 17 image with a non-root app user and `/actuator/health` healthcheck.
- `docker-compose.yml` builds and runs the packaged app with PostgreSQL for local use.
- `docker-compose.dev.yml` runs the app from the mounted source tree for active development.
- `docker-compose.cloud.yml` runs a prebuilt registry image against externally supplied cloud database settings.
- `.env.example` documents local Compose defaults.
- `.dockerignore` keeps build output, local env files, IDE files, and Git metadata out of image builds.

## Native Local Development

Check Java first:

```bash
java -version
echo "$JAVA_HOME"
```

If `java` is not found, install a JDK 17 distribution and set `JAVA_HOME` before running Gradle.

### Run With H2

This is the fastest way to start the API. It uses the default configuration in [application.properties](/home/codex/r/MoneyTransfer/src/main/resources/application.properties).

```bash
./gradlew bootRun
```

The API will start on:

```text
http://localhost:8080
```

Health check:

```bash
curl -i http://localhost:8080/actuator/health
```

### Run With PostgreSQL

Create a database:

```bash
createdb moneytransfer
```

Start the app with PostgreSQL settings:

```bash
DATABASE_URL='jdbc:postgresql://localhost:5432/moneytransfer' \
DATABASE_USERNAME='postgres' \
DATABASE_PASSWORD='postgres' \
DATABASE_DRIVER='org.postgresql.Driver' \
./gradlew bootRun
```

Flyway runs automatically and creates the schema from:

```text
src/main/resources/db/migration/V1__payment_api_schema.sql
```

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

## API Routes

Accounts:

```http
POST /accounts
GET /accounts/{accountId}
GET /accounts/{accountId}/transactions?page=0&size=20
PATCH /accounts/{accountId}/freeze
PATCH /accounts/{accountId}/unfreeze
PATCH /accounts/{accountId}/close
```

Transfers:

```http
POST /transfers/quote
POST /transfers
GET /transfers/{transferId}
POST /transfers/{transferId}/cancel
POST /transfers/{transferId}/reverse
```

Operations:

```http
GET /actuator/health
GET /actuator/metrics
GET /swagger-ui/index.html
GET /v3/api-docs
```

## Example Workflow

Create a source account:

```bash
curl -s -X POST http://localhost:8080/accounts \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: req_demo_1' \
  -d '{"currency":"USD","initialBalance":"100.00"}'
```

Create a destination account:

```bash
curl -s -X POST http://localhost:8080/accounts \
  -H 'Content-Type: application/json' \
  -d '{"currency":"USD","initialBalance":"50.00"}'
```

The returned account IDs use the public `acct_...` format. Use those values in the transfer request:

```bash
curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-transfer-001' \
  -d '{
    "sourceAccountId":"acct_SOURCE",
    "destinationAccountId":"acct_DESTINATION",
    "amount":"25.00",
    "currency":"USD",
    "description":"Demo transfer"
  }'
```

Get a transfer:

```bash
curl -s http://localhost:8080/transfers/trf_TRANSFER_ID
```

List account transactions:

```bash
curl -s 'http://localhost:8080/accounts/acct_SOURCE/transactions?page=0&size=20'
```

Reverse a completed transfer:

```bash
curl -s -X POST http://localhost:8080/transfers/trf_TRANSFER_ID/reverse
```

## Request And Response Notes

- Account IDs are generated by the service and look like `acct_...`.
- Transfer IDs are generated by the service and look like `trf_...`.
- Transfer creation requires an `Idempotency-Key` header.
- Amounts support two decimal places.
- Currency must be a three-letter uppercase code such as `USD`.
- Source and destination accounts must belong to the authenticated owner.
- Transfers are executed in one database transaction.
- Transaction history is read from immutable `ledger_entries`, newest first.

Successful transfer responses include:

```json
{
  "transferId": "trf_...",
  "sourceAccountId": "acct_...",
  "destinationAccountId": "acct_...",
  "amount": 25.00,
  "currency": "USD",
  "status": "COMPLETED",
  "description": "Demo transfer",
  "failureReason": null,
  "createdAt": "2026-06-05T...",
  "updatedAt": "2026-06-05T...",
  "correlationId": "req_..."
}
```

Errors use this shape:

```json
{
  "errorCode": "INSUFFICIENT_FUNDS",
  "message": "The source account does not have enough available balance.",
  "correlationId": "req_...",
  "details": {
    "accountId": "acct_...",
    "currency": "USD"
  }
}
```

If a request includes `X-Correlation-Id`, the same value is returned in the response header and response body. Otherwise, the service generates a `req_...` ID.

## Idempotency Behavior

`POST /transfers` requires:

```http
Idempotency-Key: unique-client-key
```

Behavior:

- Same owner, same key, same request body returns the stored transfer response.
- Same owner, same key, different request body returns `409 IDEMPOTENCY_CONFLICT`.
- Failed validation before money movement does not create ledger entries.

## Persistence Model

Flyway creates these tables:

- `accounts`
- `transfers`
- `ledger_entries`
- `idempotency_records`
- `outbox_events`
- `audit_logs`
- `webhook_endpoints`

The ledger is immutable. Reversals create compensating ledger entries and a new transfer instead of editing old entries.

## Tests

Run the test suite:

```bash
./gradlew test
```

The current integration tests cover:

- Account create/get/freeze/unfreeze/close.
- Successful transfer balance changes.
- Ledger, audit, and outbox row creation.
- Insufficient funds rollback behavior.
- Idempotency replay and conflict.
- Completed transfer reversal.

## Troubleshooting

`JAVA_HOME is not set and no 'java' command could be found in your PATH.`

Install JDK 17 and set `JAVA_HOME`, then retry:

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
```

Database connection errors with PostgreSQL:

- Confirm PostgreSQL is running.
- Confirm the database exists.
- Confirm `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `DATABASE_DRIVER`.
- Confirm the PostgreSQL driver setting is `org.postgresql.Driver`.

`401 Unauthorized` after disabling anonymous access:

- Configure a real JWT issuer/JWK source.
- Send a valid bearer token:

```bash
curl -H 'Authorization: Bearer YOUR_TOKEN' http://localhost:8080/accounts/acct_...
```

Swagger UI not loading:

- Start the app first.
- Open `http://localhost:8080/swagger-ui/index.html`.

## Development Notes

- Main package: `org.bank.moneytransfer`.
- Controllers live in `controller`.
- Business logic lives in `service`.
- JPA entities live in `domain`.
- Repositories live in `repository`.
- API DTOs live in `dto`.
- Error mapping lives in `exception`.
- Security/correlation configuration lives in `config`.

The old in-memory `/api/...` demo endpoints were removed. Use the root-level `/accounts` and `/transfers` routes.
