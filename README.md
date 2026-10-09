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

The app starts on http://localhost:8080. The H2 console is at http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:mem:prep`, user `sa`, empty password).

## Run the tests

```bash
./mvnw test
```

## Questions

| # | Question               | PR link |
|---|------------------------|---------|
| 1 | Task Manager API       |         |
| 2 | URL Shortener          |         |
| 3 | Authentication & Roles |         |
| 4 | Product Catalog        |         |
| 5 | Order Service          |         |

Video:
