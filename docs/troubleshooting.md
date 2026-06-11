# Troubleshooting

## Java Is Not Available

Error:

```text
JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

Install JDK 17 and set `JAVA_HOME`, then retry:

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
```

## PostgreSQL Connection Errors

- Confirm PostgreSQL is running.
- Confirm the database exists.
- Confirm `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `DATABASE_DRIVER`.
- Confirm the PostgreSQL driver setting is `org.postgresql.Driver`.

## Unauthorized After Disabling Anonymous Access

When `ALLOW_ANONYMOUS=false`:

- Configure a real JWT issuer or JWK source.
- Send a valid bearer token:

```bash
curl -H 'Authorization: Bearer YOUR_TOKEN' http://localhost:8080/accounts/acct_...
```

## Swagger UI Is Not Loading

- Start the app first.
- Open `http://localhost:8080/swagger-ui/index.html`.
