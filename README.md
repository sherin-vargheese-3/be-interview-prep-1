# be-interview-prep

Backend interview prep assignment: five Spring Boot features, each delivered as its own pull request.

## Stack

- Java 21, Spring Boot 3.5
- Maven (wrapper included, no local Maven install needed)
- H2 in-memory database

## Run the app

`JWT_SECRET` is required (at least 32 bytes); the app refuses to start without it. `ADMIN_EMAIL`
and `ADMIN_PASSWORD` are optional and seed an `ADMIN` user on startup.

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD='choose-a-strong-password'
./mvnw spring-boot:run
```

Every `/api/**` endpoint needs a bearer token except register and login; short-link redirects stay
public. Register, log in, then send the token:

```bash
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"a-long-password"}'
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"a-long-password"}' | jq -r .accessToken)
curl -s localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```

Tokens expire after 15 minutes. `GET /api/admin/users`, product updates and deletes, and actuator
endpoints other than health need an `ADMIN` token.

Orders need an `Idempotency-Key` header (1 to 100 characters, a UUID per logical order). Resending
the same key and body returns the original order with `200` and `Idempotent-Replayed: true`:

```bash
curl -s -X POST localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":1,"quantity":2}]}'
curl -s localhost:8080/api/orders/1 -H "Authorization: Bearer $TOKEN"
curl -s -X POST localhost:8080/api/orders/1/cancel -H "Authorization: Bearer $TOKEN"
```

The app starts on http://localhost:8080. The H2 console is at http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:mem:prep`, user `sa`, empty password).

## Run the tests

```bash
./mvnw test
```

The tests need no environment variables: a fake JWT secret and admin credentials for tests live in
`src/test/resources/config/application.properties`.

## Questions

| # | Question               | PR link |
|---|------------------------|---------|
| 1 | Task Manager API       | [#2](https://github.com/sherin-vargheese-3/be-interview-prep-1/pull/2) |
| 2 | URL Shortener          | [#4](https://github.com/sherin-vargheese-3/be-interview-prep-1/pull/4) |
| 3 | Authentication & Roles | [#8](https://github.com/sherin-vargheese-3/be-interview-prep-1/pull/8) |
| 4 | Product Catalog        | [#6](https://github.com/sherin-vargheese-3/be-interview-prep-1/pull/6) |
| 5 | Order Service          | [#10](https://github.com/sherin-vargheese-3/be-interview-prep-1/pull/10) |

Video:
