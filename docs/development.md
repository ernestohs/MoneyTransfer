# Development Guide

## Docker Development

Use the development Compose file to mount source changes into the container and run `./gradlew bootRun` inside a JDK image:

```bash
docker compose -f docker-compose.dev.yml up
```

The development stack uses the same PostgreSQL settings as the default stack, but stores database data in a separate `postgres-dev-data` volume. Gradle cache files are written to the repository's ignored `.gradle` directory.

To remove the local PostgreSQL volume and start with a fresh database:

```bash
docker compose down -v
```

## Native Local Development

Check Java first:

```bash
java -version
echo "$JAVA_HOME"
```

If `java` is not found, install a JDK 17 distribution and set `JAVA_HOME` before running Gradle.

### Run With H2

This is the fastest way to start the API. It uses the default configuration in [application.properties](../src/main/resources/application.properties).

```bash
./gradlew bootRun
```

The API starts at `http://localhost:8080`.

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

Flyway runs automatically and creates the schema from `src/main/resources/db/migration/V1__payment_api_schema.sql`.

## Tests

Run the test suite:

```bash
./gradlew test
```

The current integration tests cover:

- Account create, get, freeze, unfreeze, and close operations.
- Successful transfer balance changes.
- Ledger, audit, and outbox row creation.
- Insufficient funds rollback behavior.
- Idempotency replay and conflict.
- Completed transfer reversal.

## Project Structure

- Main package: `org.bank.moneytransfer`.
- Controllers live in `controller`.
- Business logic lives in `service`.
- JPA entities live in `domain`.
- Repositories live in `repository`.
- API DTOs live in `dto`.
- Error mapping lives in `exception`.
- Security and correlation configuration lives in `config`.

The old in-memory `/api/...` demo endpoints were removed. Use the root-level `/accounts` and `/transfers` routes.
