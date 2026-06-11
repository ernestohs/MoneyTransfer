# Architecture

MoneyTransfer is a layered Spring Boot application backed by Spring Data JPA and Flyway-managed relational persistence. See the [system design](system-design.md) for the broader design rationale and roadmap.

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

Account, transfer, ledger, audit log, and outbox state changes are written atomically where required. Completed transfers use double-entry ledger writes.

## Application Layers

- Controllers expose the REST API.
- Services contain business and transaction logic.
- JPA domain entities represent persisted state.
- Repositories provide persistence access.
- DTOs define API request and response contracts.
- Exception handlers map failures to structured API errors.
- Configuration handles security and correlation IDs.

The main package is `org.bank.moneytransfer`.

## Reliability And API Behavior

- Transfer creation is protected by owner-scoped idempotency keys.
- Transfers execute inside a database transaction.
- Transaction history is read from immutable ledger entries.
- Audit log and outbox rows are written with the state changes they describe.
- Correlation IDs connect requests, responses, and error details.

For API-level behavior and examples, see the [API guide](api-guide.md).
