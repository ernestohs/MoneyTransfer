# Professional Bank Payment API - System Design Plan

## 1. Product Goal

Build a secure, reliable, and auditable bank payment API that allows customers or partner applications to create accounts, move money between accounts, inspect transaction history, and handle failed or reversed payments.

The core product promise is:

- Money movement is correct.
- Every balance change is traceable.
- Duplicate requests do not create duplicate payments.
- The API is predictable for developers.
- Operations teams can investigate and recover from problems.

## 2. Users and Use Cases

### Primary Users

- **Customer application**: Integrates with the API to create accounts and initiate transfers.
- **End customer**: Owns an account and expects accurate balances and payment status.
- **Operations team**: Investigates failed transfers, fraud alerts, and customer support cases.
- **Compliance team**: Reviews audit trails, blocked payments, and suspicious activity.

### Core Use Cases

- Create a new account.
- Retrieve account details and balance.
- Preview a transfer before submitting it.
- Submit a money transfer.
- Track transfer status.
- List account transactions.
- Cancel a pending transfer.
- Reverse a completed transfer when policy allows it.
- Freeze or close an account.
- Export transaction data for reconciliation.

## 3. Functional Requirements

### Account Management

- Create accounts with a unique public account ID.
- Support account states:
  - `ACTIVE`
  - `FROZEN`
  - `CLOSED`
- Prevent outgoing transfers from frozen or closed accounts.
- Prevent incoming transfers to closed accounts.
- Store currency at the account level.

### Transfer Management

- Create transfers between accounts.
- Validate:
  - account existence
  - account state
  - currency compatibility
  - amount greater than zero
  - minimum and maximum transfer limits
  - sufficient available balance
- Track transfer states:
  - `PENDING`
  - `PROCESSING`
  - `COMPLETED`
  - `FAILED`
  - `CANCELLED`
  - `REVERSED`
- Support idempotency keys so retries do not duplicate payments.
- Allow cancellation only before a transfer is completed.
- Allow reversal only through a separate compensating transaction.

### Ledger and Transaction History

- Record all money movement as immutable ledger entries.
- Use double-entry accounting:
  - debit source account
  - credit destination account
- Never delete ledger entries.
- Never edit completed ledger entries.
- Use reversal entries to correct mistakes.

### API Consumer Features

- Provide OpenAPI/Swagger documentation.
- Return consistent structured error responses.
- Support pagination for transaction lists.
- Include correlation IDs in responses for debugging.
- Provide webhook events for transfer status changes.

## 4. Non-Functional Requirements

### Correctness

Payment correctness is the highest priority. The system must never lose money, create money, or allow two concurrent transfers to spend the same funds.

### Availability

The API should remain available for reads even during high transfer volume. Write operations must prefer correctness over availability when the two conflict.

### Scalability

The system should support growth from a small demo service to a production-style service by separating API handling, business logic, persistence, and asynchronous processing.

### Security

The system must protect account data, enforce authorization, validate inputs, and preserve an audit trail for sensitive operations.

### Observability

Every transfer should be traceable through logs, metrics, audit records, and correlation IDs.

## 5. High-Level Architecture

```text
Client / Partner App
        |
        v
REST API Controller
        |
        v
Application Service Layer
        |
        v
Domain Layer: Account, Transfer, Ledger
        |
        v
Repository Layer
        |
        v
PostgreSQL Database
        |
        v
Outbox Worker -> Webhooks / Notifications
```

### Layer Responsibilities

- `controller`: Receives HTTP requests, validates request shape, maps DTOs.
- `service`: Coordinates use cases and database transactions.
- `domain`: Holds business rules for accounts, transfers, and ledger behavior.
- `repository`: Reads and writes database records.
- `dto`: Defines external API request and response models.
- `exception`: Centralizes error handling and response formatting.
- `config`: Security, database, rate limiting, and observability configuration.

The controller should not contain banking logic. Business decisions belong in the service and domain layers so they can be tested independently.

## 6. Data Model

### `accounts`

| Field | Purpose |
| --- | --- |
| `id` | Internal database ID |
| `public_id` | External account identifier |
| `owner_id` | Customer or tenant owner |
| `currency` | Account currency |
| `status` | `ACTIVE`, `FROZEN`, or `CLOSED` |
| `available_balance` | Spendable balance |
| `created_at` | Creation timestamp |
| `updated_at` | Last update timestamp |
| `version` | Optimistic locking version |

### `transfers`

| Field | Purpose |
| --- | --- |
| `id` | Internal database ID |
| `public_id` | External transfer identifier |
| `source_account_id` | Account sending funds |
| `destination_account_id` | Account receiving funds |
| `amount` | Transfer amount |
| `currency` | Transfer currency |
| `status` | Current transfer state |
| `idempotency_key` | Duplicate request protection |
| `failure_reason` | Reason if transfer fails |
| `created_at` | Creation timestamp |
| `updated_at` | Last update timestamp |

### `ledger_entries`

| Field | Purpose |
| --- | --- |
| `id` | Internal database ID |
| `transfer_id` | Related transfer |
| `account_id` | Account affected |
| `direction` | `DEBIT` or `CREDIT` |
| `amount` | Ledger amount |
| `currency` | Ledger currency |
| `balance_after` | Account balance after entry |
| `created_at` | Immutable timestamp |

### `idempotency_records`

| Field | Purpose |
| --- | --- |
| `idempotency_key` | Client-provided unique key |
| `request_hash` | Hash of original request body |
| `response_body` | Stored response for safe retry |
| `status_code` | Stored HTTP status |
| `created_at` | Creation timestamp |
| `expires_at` | Cleanup timestamp |

## 7. API Design

### Account Endpoints

```http
POST /accounts
GET /accounts/{accountId}
GET /accounts/{accountId}/transactions
PATCH /accounts/{accountId}/freeze
PATCH /accounts/{accountId}/unfreeze
PATCH /accounts/{accountId}/close
```

### Transfer Endpoints

```http
POST /transfers/quote
POST /transfers
GET /transfers/{transferId}
POST /transfers/{transferId}/cancel
POST /transfers/{transferId}/reverse
```

### Example Transfer Request

```json
{
  "sourceAccountId": "acct_123",
  "destinationAccountId": "acct_456",
  "amount": "250.00",
  "currency": "USD",
  "description": "Invoice payment"
}
```

Headers:

```http
Idempotency-Key: 7f6b8a81-4f65-4b49-9a42-77e2c9a31f62
```

### Example Structured Error

```json
{
  "errorCode": "INSUFFICIENT_FUNDS",
  "message": "The source account does not have enough available balance.",
  "correlationId": "req_8Lx92",
  "details": {
    "accountId": "acct_123",
    "currency": "USD"
  }
}
```

## 8. Transfer Flow

```text
1. Client sends POST /transfers with Idempotency-Key.
2. API validates request format.
3. Service checks whether idempotency key already exists.
4. Service loads source and destination accounts inside a database transaction.
5. Service validates account state, limits, currency, and balance.
6. Service creates a transfer record with status PROCESSING.
7. Service creates two immutable ledger entries:
   - debit source account
   - credit destination account
8. Service updates account balances.
9. Service marks transfer COMPLETED.
10. Service stores idempotency response.
11. Outbox worker publishes transfer.completed webhook.
```

## 9. Consistency and Concurrency Strategy

### Database Transactions

The entire transfer operation should run inside one database transaction. If any step fails, the transaction rolls back and no partial money movement is committed.

### Locking Choice

Use pessimistic locking for the source and destination account rows during transfer execution:

```sql
SELECT * FROM accounts WHERE id IN (?, ?) FOR UPDATE;
```

This prevents two concurrent transfers from spending the same funds. For a banking-style system, this is easier to reason about than optimistic locking and is appropriate for the core payment path.

### Deadlock Prevention

When locking two accounts, always lock them in a deterministic order, such as ascending internal account ID. This avoids two transfers locking the same accounts in opposite order.

### Idempotency

Idempotency should be enforced with a unique constraint on:

```text
owner_id + idempotency_key
```

If the same key is reused with the same request body, return the original response. If the same key is reused with a different request body, return `409 Conflict`.

## 10. Reliability Features

### Outbox Pattern

Webhook events should not be sent directly inside the transfer transaction. Instead:

1. Save the transfer.
2. Save ledger entries.
3. Save an outbox event in the same transaction.
4. A background worker publishes the event after commit.

This prevents a transfer from being completed without a reliable record of the event that must be sent.

### Webhook Delivery

Webhook events:

- `transfer.created`
- `transfer.completed`
- `transfer.failed`
- `transfer.cancelled`
- `transfer.reversed`
- `account.frozen`

Webhook delivery should include retries with exponential backoff and signed payloads.

### Failure Handling

- If validation fails, return `400 Bad Request`.
- If authorization fails, return `403 Forbidden`.
- If an account does not exist, return `404 Not Found`.
- If an idempotency conflict occurs, return `409 Conflict`.
- If the system is temporarily unavailable, return `503 Service Unavailable`.

## 11. Security and Compliance

### Authentication

Use OAuth2/JWT for customer-facing APIs or API keys for partner integrations. Tokens should include tenant or owner identity.

### Authorization

Every account and transfer access must verify ownership. A user should not be able to retrieve another user's account or transfer by guessing an ID.

### Public IDs

Expose public IDs like `acct_...` and `trf_...` instead of internal database IDs.

### Validation

Validate all request fields:

- amount format and precision
- supported currency
- source and destination account IDs
- description length
- idempotency key format

### Audit Logging

Audit logs should record sensitive actions:

- account created
- account frozen
- account closed
- transfer created
- transfer completed
- transfer cancelled
- transfer reversed
- admin override

Audit logs should include actor, timestamp, action, target resource, and correlation ID.

### Compliance Hooks

For a professional payment system, leave room for:

- KYC status checks
- sanctions screening
- fraud scoring
- manual review queues
- transfer limits by risk tier

## 12. Observability

### Logs

Use structured logs with:

- `correlationId`
- `accountId`
- `transferId`
- `status`
- `errorCode`

### Metrics

Track:

- transfer success count
- transfer failure count
- average transfer latency
- webhook delivery success rate
- insufficient funds errors
- idempotency replay count
- database transaction duration

### Health Checks

Expose:

```http
GET /actuator/health
GET /actuator/metrics
```

Health should check database connectivity and background worker status.

## 13. Testing Strategy

### Unit Tests

- account state transitions
- amount validation
- transfer limit rules
- ledger entry generation
- cancellation and reversal rules

### Integration Tests

- repository persistence
- Flyway migrations
- transaction rollback behavior
- idempotency record persistence
- webhook outbox persistence

### API Tests

- successful account creation
- successful transfer
- insufficient funds
- frozen account transfer rejection
- duplicate idempotency key replay
- idempotency key conflict
- transaction pagination

### Concurrency Tests

- two simultaneous transfers from the same account
- transfer between the same two accounts in opposite directions
- duplicate transfer retries under load

## 14. Key Design Tradeoffs

### Relational Database vs NoSQL

Use PostgreSQL because money movement needs transactions, constraints, row locks, and strong consistency. A NoSQL database could scale horizontally, but it would make ledger correctness and atomic transfers harder.

### Pessimistic Locking vs Optimistic Locking

Pessimistic locking is selected for the money movement path because it prevents overspending with simpler reasoning. Optimistic locking can be useful for low-conflict updates, but payment execution is a high-risk operation where correctness matters more than maximum throughput.

### Synchronous Transfer vs Asynchronous Transfer

For internal account-to-account transfers, complete synchronously inside a database transaction. For external bank rails, create the transfer synchronously but process settlement asynchronously because external systems may take minutes or days.

### Balance Table vs Ledger-Only Balance

Store `available_balance` on the account for fast reads, but treat the ledger as the source of truth for audit and reconciliation. Periodic reconciliation can compare account balances against ledger totals.

## 15. Implementation Roadmap

### Phase 1: Core Banking Foundation

1. Create layered Spring Boot project structure.
2. Add PostgreSQL and Flyway migrations.
3. Implement account entity, repository, and API.
4. Implement transfer entity and service.
5. Add immutable double-entry ledger records.
6. Add transaction-safe balance updates.

### Phase 2: API Reliability

1. Add structured error responses.
2. Add idempotency keys.
3. Add pessimistic locking.
4. Add pagination for transaction history.
5. Add concurrency and rollback tests.

### Phase 3: Professional API Features

1. Add transfer quote endpoint.
2. Add transfer status lifecycle.
3. Add cancel and reverse flows.
4. Add webhook outbox and delivery worker.
5. Add OpenAPI/Swagger documentation.

### Phase 4: Security and Operations

1. Add authentication and authorization.
2. Add rate limiting.
3. Add audit logs.
4. Add admin freeze, unfreeze, and reversal actions.
5. Add metrics, health checks, and dashboards.

### Phase 5: Compliance and Scale

1. Add KYC and sanctions screening hooks.
2. Add fraud scoring hooks.
3. Add reconciliation exports.
4. Add tenant-level transfer limits.
5. Add archival strategy for old ledger and webhook records.

## 16. What Makes This Design Professional

This design goes beyond simple CRUD. It treats payments as a correctness-critical workflow with clear state transitions, immutable ledger entries, safe retries, account locking, auditability, and operational recovery.

The most important design decision is using a transactional ledger model. Account balances are useful for fast reads, but the ledger explains every balance change. That makes the system easier to audit, reconcile, debug, and trust.
