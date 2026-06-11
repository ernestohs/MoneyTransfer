# MoneyTransfer

MoneyTransfer is a Spring Boot payment API for creating accounts, transferring money, reading ledger-backed transaction history, and safely operating account and transfer state.

## Features

- REST APIs for accounts, transfers, and transaction history.
- Transactional double-entry ledger writes.
- Idempotent transfer creation.
- Account freeze, unfreeze, and close operations.
- Transfer cancellation and reversal.
- Atomic audit log and outbox writes.
- Structured errors with correlation IDs.
- PostgreSQL persistence with Flyway migrations and an H2 development fallback.
- Actuator health and metrics endpoints.
- Swagger/OpenAPI UI.

## Requirements

- Docker and Docker Compose for the recommended local workflow.
- Java 17 for native development.
- The Gradle wrapper included in this repository.

## Quick Start

Start the API and PostgreSQL:

```bash
cp .env.example .env
docker compose up --build
```

The API is available at `http://localhost:8080`.

Verify the service:

```bash
curl -i http://localhost:8080/actuator/health
```

Stop the stack:

```bash
docker compose down
```

## API Overview

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

See the [API guide](docs/api-guide.md) for request examples and behavior details.

## Documentation

- [Development guide](docs/development.md)
- [API guide](docs/api-guide.md)
- [Deployment guide](docs/deployment.md)
- [Architecture](docs/architecture.md)
- [System design](docs/system-design.md)
- [Troubleshooting](docs/troubleshooting.md)

## Testing

Run the test suite:

```bash
./gradlew test
```
